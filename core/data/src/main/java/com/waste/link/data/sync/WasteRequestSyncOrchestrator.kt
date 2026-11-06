package com.waste.link.data.sync

import com.waste.link.data.auth.SessionStateProvider
import com.waste.link.data.local.entity.LocalWasteRequestRecord
import com.waste.link.data.local.model.LocalSyncStatus
import com.waste.link.data.mapper.toDomainOrNull
import com.waste.link.data.remote.RemoteRequestOutcome
import com.waste.link.data.remote.WasteRequestRemoteDataSource
import com.waste.link.data.repository.LocalWasteRequestRepository
import com.waste.link.domain.model.SessionState
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Orchestrates reliable, authenticated synchronization between locally persisted requests
 * and Cloud Firestore.
 *
 * Strict Architectural Invariants:
 * 1. Derives authenticated identity strictly from [SessionStateProvider]. Caller-supplied UID
 *    must match active server-verified session.
 * 2. Unmapped legacy records (`serviceZoneId == "legacy_unmapped_zone"`) are permanently quarantined
 *    and never passed to remote backend.
 * 3. Concurrent deduplication ensures only one upload runs per request ID.
 * 4. Idempotent reconciliation avoids duplicate writes.
 * 5. Bounded exponential backoff with max 3 automatic retries.
 * 6. Stale UPLOADING recovery resets interrupted uploads to QUEUED_OFFLINE.
 */
@Singleton
class WasteRequestSyncOrchestrator @Inject constructor(
    private val localRepository: LocalWasteRequestRepository,
    private val remoteDataSource: WasteRequestRemoteDataSource,
    private val sessionStateProvider: SessionStateProvider,
    private val syncScheduler: WasteRequestSyncScheduler
) {

    companion object {
        const val MAX_RETRIES = 3
        const val BASE_RETRY_DELAY_MS = 3_000L
        const val MAX_RETRY_DELAY_MS = 60_000L
        const val DEFAULT_STALE_THRESHOLD_MS = 60_000L
        const val LEGACY_UNMAPPED_ZONE_ID = "legacy_unmapped_zone"
    }

    internal var clock: () -> Long = { System.currentTimeMillis() }

    private val _events = MutableSharedFlow<SyncEvent>(extraBufferCapacity = 64)
    val events: SharedFlow<SyncEvent> = _events.asSharedFlow()

    private val activeSyncingIds = Collections.synchronizedSet(mutableSetOf<String>())

    suspend fun synchronizeSingleRequest(
        authenticatedUid: String,
        requestId: String
    ): SyncResult {
        // Step 1: Authentication & UID derivation verification
        val currentSession = sessionStateProvider.sessionState.value
        if (currentSession !is SessionState.Authenticated || !currentSession.isServerVerified) {
            val reason = "User session is not active and server-verified."
            _events.emit(SyncEvent.AuthUnavailable(reason, clock()))
            return SyncResult.AuthUnavailable(reason)
        }
        val verifiedAuthUid = currentSession.user.id
        if (authenticatedUid != verifiedAuthUid) {
            val reason = "Security violation: Authenticated UID mismatch ($authenticatedUid vs $verifiedAuthUid)"
            _events.emit(SyncEvent.RequestRejected(requestId, reason, clock()))
            return SyncResult.Rejected(requestId, "UID_MISMATCH", reason)
        }

        // Step 2: Concurrency deduplication
        if (!activeSyncingIds.add(requestId)) {
            return SyncResult.QueuedOffline(requestId, "Upload already in progress")
        }

        try {
            // Step 3: Local persistence retrieval
            val record = localRepository.getLocalRecord(requestId, authenticatedUid)
                ?: return SyncResult.Failure(
                    requestId,
                    NoSuchElementException("Local request '$requestId' not found for user '$authenticatedUid'.")
                )

            // Step 4: Terminal status check
            if (record.syncStatus == LocalSyncStatus.SYNCED) {
                return SyncResult.Success(requestId, record.requestRef ?: "")
            }

            // Step 5: Legacy unmapped zone quarantine
            if (record.serviceZoneId == LEGACY_UNMAPPED_ZONE_ID) {
                localRepository.updateSyncStatus(
                    id = requestId,
                    clientUid = authenticatedUid,
                    newStatus = LocalSyncStatus.SYNC_FAILED,
                    errorMessage = "Legacy unmapped service zone quarantined. Resolution required before remote sync."
                )
                _events.emit(SyncEvent.LegacyRecordQuarantined(requestId, clock()))
                return SyncResult.Rejected(requestId, "LEGACY_UNMAPPED_ZONE", "Legacy unmapped record quarantined.")
            }

            // Step 6: Domain conversion check
            val domainRequest = record.toDomainOrNull()
            if (domainRequest == null) {
                localRepository.updateSyncStatus(
                    id = requestId,
                    clientUid = authenticatedUid,
                    newStatus = LocalSyncStatus.SYNC_FAILED,
                    errorMessage = "Local record contains invalid domain state."
                )
                return SyncResult.Rejected(requestId, "MALFORMED_LOCAL_RECORD", "Record cannot be converted to domain model.")
            }

            // Step 6b: Backoff window check (Finding 1)
            if (record.retryCount > 0 && record.lastSyncAttemptEpochMs != null) {
                val minDelay = computeBackoffDelay(record.retryCount)
                val elapsed = clock() - record.lastSyncAttemptEpochMs
                if (elapsed < minDelay) {
                    val remainingMs = minDelay - elapsed
                    return SyncResult.QueuedOffline(
                        requestId,
                        "Retry backoff in progress ($elapsed/${minDelay}ms elapsed; $remainingMs ms remaining)."
                    )
                }
            }

            // Step 7: Transition to UPLOADING
            localRepository.updateSyncStatus(
                id = requestId,
                clientUid = authenticatedUid,
                newStatus = LocalSyncStatus.UPLOADING,
                attemptEpochMs = clock()
            )
            _events.emit(SyncEvent.UploadStarted(requestId, authenticatedUid, clock()))

            // Step 8: Remote submission
            val outcome = remoteDataSource.createRequest(authenticatedUid, domainRequest)

            // Step 8b: Revalidate session post-submission before committing result
            val postCallSession = sessionStateProvider.sessionState.value
            val isSessionStillValid = postCallSession is SessionState.Authenticated &&
                postCallSession.isServerVerified &&
                postCallSession.user.id == authenticatedUid

            if (!isSessionStillValid) {
                // Active session changed or logged out during remote upload.
                // Reset original user's record from UPLOADING to QUEUED_OFFLINE to prevent corruption or stranded upload.
                localRepository.updateSyncStatus(
                    id = requestId,
                    clientUid = authenticatedUid,
                    newStatus = LocalSyncStatus.QUEUED_OFFLINE,
                    errorMessage = "Upload aborted due to session change during in-flight synchronization."
                )
                val reason = "Session changed or invalidated during upload of request '$requestId'."
                _events.emit(SyncEvent.AuthUnavailable(reason, clock()))
                return SyncResult.AuthUnavailable(reason)
            }

            return when (outcome) {
                is RemoteRequestOutcome.Success -> {
                    // Finding 3: Validate authoritative server timestamps are present
                    if (outcome.serverCreatedAtEpochMs <= 0L || outcome.serverUpdatedAtEpochMs <= 0L) {
                        return handleTransientFailure(
                            requestId = requestId,
                            authenticatedUid = authenticatedUid,
                            record = record,
                            errorMessage = "Remote request created but server timestamps are unresolved (${outcome.serverCreatedAtEpochMs}, ${outcome.serverUpdatedAtEpochMs}).",
                            cause = IllegalStateException("Server timestamps unresolved on Success outcome.")
                        )
                    }

                    // Finding 3 & Additional Review: Persist server timestamps and verify Room update succeeds
                    val updated = localRepository.updateSyncSuccess(
                        id = requestId,
                        clientUid = authenticatedUid,
                        requestRef = outcome.requestRef,
                        serverCreatedAtEpochMs = outcome.serverCreatedAtEpochMs,
                        serverUpdatedAtEpochMs = outcome.serverUpdatedAtEpochMs,
                        attemptEpochMs = clock()
                    )

                    if (!updated) {
                        val errMsg = "Failed to persist SYNCED state and server timestamps to local Room persistence for request '$requestId'."
                        _events.emit(
                            SyncEvent.UploadFailed(
                                requestId = requestId,
                                errorMessage = errMsg,
                                isRetryable = false,
                                retryAttempt = record.retryCount,
                                timestampEpochMs = clock()
                            )
                        )
                        return SyncResult.Failure(requestId, IllegalStateException(errMsg))
                    }

                    _events.emit(SyncEvent.UploadSucceeded(requestId, outcome.requestRef, clock()))
                    SyncResult.Success(requestId, outcome.requestRef)
                }

                is RemoteRequestOutcome.AlreadyExists -> {
                    // Finding 3: Validate authoritative server timestamps on reconciled duplicate
                    if (outcome.serverCreatedAtEpochMs <= 0L || outcome.serverUpdatedAtEpochMs <= 0L) {
                        return handleTransientFailure(
                            requestId = requestId,
                            authenticatedUid = authenticatedUid,
                            record = record,
                            errorMessage = "Existing remote document has unresolved timestamps (${outcome.serverCreatedAtEpochMs}, ${outcome.serverUpdatedAtEpochMs}).",
                            cause = IllegalStateException("Server timestamps unresolved on AlreadyExists outcome.")
                        )
                    }

                    val updated = localRepository.updateSyncSuccess(
                        id = requestId,
                        clientUid = authenticatedUid,
                        requestRef = outcome.requestRef,
                        serverCreatedAtEpochMs = outcome.serverCreatedAtEpochMs,
                        serverUpdatedAtEpochMs = outcome.serverUpdatedAtEpochMs,
                        attemptEpochMs = clock()
                    )

                    if (!updated) {
                        val errMsg = "Failed to persist reconciled SYNCED state to local Room persistence for request '$requestId'."
                        _events.emit(
                            SyncEvent.UploadFailed(
                                requestId = requestId,
                                errorMessage = errMsg,
                                isRetryable = false,
                                retryAttempt = record.retryCount,
                                timestampEpochMs = clock()
                            )
                        )
                        return SyncResult.Failure(requestId, IllegalStateException(errMsg))
                    }

                    _events.emit(SyncEvent.DuplicateReconciled(requestId, outcome.requestRef, clock()))
                    _events.emit(SyncEvent.UploadSucceeded(requestId, outcome.requestRef, clock()))
                    SyncResult.Success(requestId, outcome.requestRef)
                }

                is RemoteRequestOutcome.Rejected -> {
                    localRepository.updateSyncStatus(
                        id = requestId,
                        clientUid = authenticatedUid,
                        newStatus = LocalSyncStatus.SYNC_FAILED,
                        errorMessage = "[${outcome.errorCode}] ${outcome.message}"
                    )
                    _events.emit(SyncEvent.RequestRejected(requestId, outcome.message, clock()))
                    _events.emit(
                        SyncEvent.UploadFailed(
                            requestId = requestId,
                            errorMessage = outcome.message,
                            isRetryable = false,
                            retryAttempt = record.retryCount,
                            timestampEpochMs = clock()
                        )
                    )
                    SyncResult.Rejected(requestId, outcome.errorCode, outcome.message)
                }

                is RemoteRequestOutcome.TransientFailure -> {
                    handleTransientFailure(
                        requestId = requestId,
                        authenticatedUid = authenticatedUid,
                        record = record,
                        errorMessage = outcome.message,
                        cause = outcome.cause
                    )
                }
            }
        } finally {
            activeSyncingIds.remove(requestId)
        }
    }

    private suspend fun handleTransientFailure(
        requestId: String,
        authenticatedUid: String,
        record: LocalWasteRequestRecord,
        errorMessage: String,
        cause: Throwable
    ): SyncResult {
        val nextRetry = record.retryCount + 1
        return if (nextRetry <= MAX_RETRIES) {
            val delayMs = computeBackoffDelay(nextRetry)
            // Finding 1: Retryable transient failure transitions to QUEUED_OFFLINE so it remains eligible for worker attempts
            localRepository.updateSyncStatus(
                id = requestId,
                clientUid = authenticatedUid,
                newStatus = LocalSyncStatus.QUEUED_OFFLINE,
                errorMessage = errorMessage,
                attemptEpochMs = clock(),
                retryCount = nextRetry
            )
            _events.emit(
                SyncEvent.UploadFailed(
                    requestId = requestId,
                    errorMessage = errorMessage,
                    isRetryable = true,
                    retryAttempt = nextRetry,
                    timestampEpochMs = clock()
                )
            )
            syncScheduler.scheduleDelayedSync(authenticatedUid, delayMs, nextRetry)
            _events.emit(SyncEvent.RetryScheduled(requestId, nextRetry, delayMs, clock()))
            SyncResult.QueuedOffline(requestId, "Transient network failure; retry $nextRetry scheduled.")
        } else {
            // Finding 1: Exhausted retries transition permanently to SYNC_FAILED
            localRepository.updateSyncStatus(
                id = requestId,
                clientUid = authenticatedUid,
                newStatus = LocalSyncStatus.SYNC_FAILED,
                errorMessage = "Max retries exceeded: $errorMessage",
                attemptEpochMs = clock(),
                retryCount = nextRetry
            )
            _events.emit(
                SyncEvent.UploadFailed(
                    requestId = requestId,
                    errorMessage = "Max retries exceeded: $errorMessage",
                    isRetryable = false,
                    retryAttempt = nextRetry,
                    timestampEpochMs = clock()
                )
            )
            SyncResult.Failure(requestId, cause)
        }
    }

    suspend fun synchronizeQueuedRequests(authenticatedUid: String): List<SyncResult> {
        recoverInterruptedUploads(authenticatedUid)
        val queued = localRepository.getQueuedRecords(authenticatedUid)
        val results = queued.map { record ->
            synchronizeSingleRequest(authenticatedUid, record.id)
        }

        // Finding 1 Requirement 9: Ensure delayed sync is scheduled for any remaining records in backoff
        val remainingQueued = localRepository.getQueuedRecords(authenticatedUid)
        var earliestDelay: Long? = null
        var earliestAttempt: Int = 1
        for (rec in remainingQueued) {
            val lastAttempt = rec.lastSyncAttemptEpochMs
            if (rec.retryCount in 1..MAX_RETRIES && lastAttempt != null) {
                val delay = computeBackoffDelay(rec.retryCount)
                val elapsed = clock() - lastAttempt
                val remaining = delay - elapsed
                if (remaining > 0L) {
                    if (earliestDelay == null || remaining < earliestDelay) {
                        earliestDelay = remaining
                        earliestAttempt = rec.retryCount
                    }
                }
            }
        }
        if (earliestDelay != null) {
            syncScheduler.scheduleDelayedSync(authenticatedUid, earliestDelay, earliestAttempt)
        }

        return results
    }

    suspend fun recoverInterruptedUploads(
        authenticatedUid: String,
        staleThresholdMs: Long = DEFAULT_STALE_THRESHOLD_MS
    ): Int {
        val staleBefore = clock() - staleThresholdMs
        return localRepository.recoverStaleUploadingRequests(authenticatedUid, staleBefore)
    }

    suspend fun retryFailedRequest(
        authenticatedUid: String,
        requestId: String
    ): SyncResult {
        localRepository.getLocalRecord(requestId, authenticatedUid)
            ?: return SyncResult.Failure(requestId, NoSuchElementException("Local request '$requestId' not found."))

        localRepository.updateSyncStatus(
            id = requestId,
            clientUid = authenticatedUid,
            newStatus = LocalSyncStatus.QUEUED_OFFLINE,
            errorMessage = null,
            retryCount = 0
        )
        return synchronizeSingleRequest(authenticatedUid, requestId)
    }

    fun computeBackoffDelay(attempt: Int): Long {
        if (attempt <= 0) return 0L
        val factor = 1 shl (attempt - 1).coerceAtMost(5)
        return (BASE_RETRY_DELAY_MS * factor).coerceAtMost(MAX_RETRY_DELAY_MS)
    }
}
