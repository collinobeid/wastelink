package com.waste.link.data.repository

import com.waste.link.data.auth.SessionStateProvider
import com.waste.link.data.local.model.LocalSyncStatus
import com.waste.link.data.sync.SyncResult
import com.waste.link.data.sync.WasteRequestSyncOrchestrator
import com.waste.link.domain.model.SessionState
import com.waste.link.domain.model.WasteRequest
import com.waste.link.domain.repository.CreateWasteRequestOutcome
import com.waste.link.domain.repository.WasteRequestRepository
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Concrete implementation of the domain [WasteRequestRepository] contract.
 *
 * Strict Architecture Invariants:
 * 1. Derives authenticated identity strictly from [SessionStateProvider].
 * 2. Persists validated request locally to Room as [LocalSyncStatus.QUEUED_OFFLINE] first.
 * 3. Triggers immediate sync attempt via [WasteRequestSyncOrchestrator].
 * 4. Never reports [CreateWasteRequestOutcome.Success] unless the backend confirms the write
 *    with authoritative reference and timestamps.
 * 5. On transient offline / network failure, request remains persisted locally with [LocalSyncStatus.SYNC_FAILED]
 *    or [LocalSyncStatus.QUEUED_OFFLINE], scheduled for background retry via WorkManager.
 */
@Singleton
class WasteRequestRepositoryImpl @Inject constructor(
    private val localRepository: LocalWasteRequestRepository,
    private val syncOrchestrator: WasteRequestSyncOrchestrator,
    private val sessionStateProvider: SessionStateProvider,
    private val syncScheduler: com.waste.link.data.sync.WasteRequestSyncScheduler
) : WasteRequestRepository {

    override suspend fun createWasteRequest(request: WasteRequest): CreateWasteRequestOutcome {
        val currentSession = sessionStateProvider.sessionState.value
        if (currentSession !is SessionState.Authenticated || !currentSession.isServerVerified) {
            return CreateWasteRequestOutcome.Failure(
                error = IllegalStateException("Cannot create request: User session is not active and server-verified.")
            )
        }

        val authenticatedUid = currentSession.user.id
        if (request.clientUid != authenticatedUid) {
            return CreateWasteRequestOutcome.Failure(
                error = IllegalArgumentException("Request clientUid ('${request.clientUid}') does not match authenticated user ('$authenticatedUid').")
            )
        }

        // 1. Immediate local persistence as QUEUED_OFFLINE
        try {
            localRepository.saveLocalRequest(
                authenticatedUid = authenticatedUid,
                request = request,
                syncStatus = LocalSyncStatus.QUEUED_OFFLINE
            )
        } catch (e: Exception) {
            return CreateWasteRequestOutcome.Failure(
                error = e,
                message = "Failed to persist request locally: ${e.message}"
            )
        }

        // 2. Trigger immediate sync via orchestrator
        return when (val syncResult = syncOrchestrator.synchronizeSingleRequest(authenticatedUid, request.id)) {
            is SyncResult.Success -> {
                val confirmed = localRepository.getLocalRequest(request.id, authenticatedUid)
                    ?: request.copy(requestRef = syncResult.requestRef)
                CreateWasteRequestOutcome.Success(confirmedRequest = confirmed)
            }

            is SyncResult.QueuedOffline -> {
                // Connect offline failure to actual WorkManager background sync
                syncScheduler.scheduleImmediateSync(authenticatedUid)
                CreateWasteRequestOutcome.Failure(
                    error = IllegalStateException(syncResult.reason),
                    message = syncResult.reason
                )
            }

            is SyncResult.Rejected -> {
                CreateWasteRequestOutcome.Failure(
                    error = IllegalStateException("[${syncResult.errorCode}] ${syncResult.message}"),
                    message = syncResult.message
                )
            }

            is SyncResult.AuthUnavailable -> {
                CreateWasteRequestOutcome.Failure(
                    error = IllegalStateException("Authentication unavailable: ${syncResult.reason}"),
                    message = syncResult.reason
                )
            }

            is SyncResult.Failure -> {
                // Connect transient failure to actual WorkManager background sync
                syncScheduler.scheduleImmediateSync(authenticatedUid)
                CreateWasteRequestOutcome.Failure(
                    error = syncResult.error,
                    message = syncResult.error.message ?: "Failed to synchronize waste request"
                )
            }
        }
    }
}
