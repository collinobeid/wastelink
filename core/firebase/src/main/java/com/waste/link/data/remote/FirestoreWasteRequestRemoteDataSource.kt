package com.waste.link.data.remote

import androidx.annotation.VisibleForTesting
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.waste.link.data.remote.dto.WasteRequestDto
import com.waste.link.data.remote.mapper.WasteRequestRemoteMapper
import com.waste.link.domain.model.RequestRefSpecification
import com.waste.link.domain.model.WasteRequest
import kotlinx.coroutines.tasks.await
import java.io.IOException
import java.util.UUID
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Production implementation of [WasteRequestRemoteDataSource] backed by Cloud Firestore.
 *
 * Strict Architecture & Security Invariants:
 * 1. Target collection is strictly `/waste_requests/{id}`.
 * 2. Caller's [authenticatedUid] must match [request.clientUid] exactly.
 * 3. Never uploads requests with serviceZoneId == "legacy_unmapped_zone".
 * 4. Idempotently reconciles prior submissions via [RemoteRequestOutcome.AlreadyExists].
 * 5. Deterministic canonical reference formatted via [RequestRefSpecification].
 */
@Singleton
class FirestoreWasteRequestRemoteDataSource internal constructor(
    private val firestoreProvider: () -> FirebaseFirestore
) : WasteRequestRemoteDataSource {

    @Inject
    constructor(firestore: FirebaseFirestore) : this({ firestore })

    private val firestore: FirebaseFirestore
        get() = firestoreProvider()

    companion object {
        const val WASTE_REQUESTS_COLLECTION = "waste_requests"
        const val LEGACY_UNMAPPED_ZONE_ID = "legacy_unmapped_zone"
    }

    private val simulatedNextFailure = AtomicReference<Throwable?>(null)

    @VisibleForTesting
    fun setSimulatedNextFailure(throwable: Throwable?) {
        simulatedNextFailure.set(throwable)
    }

    override suspend fun createRequest(
        authenticatedUid: String,
        request: WasteRequest
    ): RemoteRequestOutcome {
        simulatedNextFailure.getAndSet(null)?.let { simulated ->
            return mapException(simulated)
        }

        // Invariant 1: Caller identity check
        if (authenticatedUid.isBlank()) {
            return RemoteRequestOutcome.Rejected(
                errorCode = "UNAUTHENTICATED",
                message = "Authenticated UID must not be blank",
                requiresUserIntervention = true
            )
        }
        if (request.clientUid != authenticatedUid) {
            return RemoteRequestOutcome.Rejected(
                errorCode = "UID_MISMATCH",
                message = "Cannot create request: clientUid ('${request.clientUid}') does not match authenticated UID ('$authenticatedUid')",
                requiresUserIntervention = true
            )
        }

        // Invariant 2: Legacy unmapped zone quarantine
        if (request.serviceZoneId == LEGACY_UNMAPPED_ZONE_ID) {
            return RemoteRequestOutcome.Rejected(
                errorCode = "LEGACY_UNMAPPED_ZONE",
                message = "Cannot synchronize request with unmapped legacy service zone ('$LEGACY_UNMAPPED_ZONE_ID')",
                requiresUserIntervention = true
            )
        }

        val docRef = firestore.collection(WASTE_REQUESTS_COLLECTION).document(request.id)

        // Invariant 3: Idempotency pre-check
        try {
            val snapshot = docRef.get().await()
            if (snapshot.exists()) {
                val dto = snapshot.toObject(WasteRequestDto::class.java)
                    ?: return RemoteRequestOutcome.Rejected(
                        errorCode = "MALFORMED_REMOTE_DOC",
                        message = "Existing remote document could not be parsed."
                    )
                val existingRequest = WasteRequestRemoteMapper.toDomain(dto, request.serviceZoneId)
                val serverCreatedAt = dto.createdAt?.toDate()?.time ?: System.currentTimeMillis()
                val serverUpdatedAt = dto.updatedAt?.toDate()?.time ?: System.currentTimeMillis()
                return RemoteRequestOutcome.AlreadyExists(
                    existingRequest = existingRequest,
                    requestRef = dto.requestRef,
                    serverCreatedAtEpochMs = serverCreatedAt,
                    serverUpdatedAtEpochMs = serverUpdatedAt
                )
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            return mapException(e)
        }

        // Invariant 4: Canonical reference allocation
        val allocatedRef = if (request.requestRef != null && RequestRefSpecification.isValid(request.requestRef)) {
            request.requestRef!!
        } else {
            val entropy = (UUID.randomUUID().mostSignificantBits and 0x3FFFFFFF).toInt()
            RequestRefSpecification.formatNonCanonicalPreview(entropy, System.currentTimeMillis())
        }

        // Invariant 5: Safe payload mapping matching firestore.rules whitelist
        val dto = WasteRequestRemoteMapper.toDto(request, allocatedRef = allocatedRef)
        val creationMap = dto.toFirestoreCreationMap()

        return try {
            docRef.set(creationMap).await()
            val now = System.currentTimeMillis()
            val confirmedRequest = request.copy(
                requestRef = allocatedRef,
                createdAtEpochMs = request.createdAtEpochMs ?: now,
                updatedAtEpochMs = now
            )
            RemoteRequestOutcome.Success(
                confirmedRequest = confirmedRequest,
                requestRef = allocatedRef,
                serverCreatedAtEpochMs = confirmedRequest.createdAtEpochMs ?: now,
                serverUpdatedAtEpochMs = now
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            mapException(e)
        }
    }

    override suspend fun getRequestById(
        authenticatedUid: String,
        requestId: String
    ): RemoteRequestOutcome {
        simulatedNextFailure.getAndSet(null)?.let { simulated ->
            return mapException(simulated)
        }

        if (authenticatedUid.isBlank()) {
            return RemoteRequestOutcome.Rejected(
                errorCode = "UNAUTHENTICATED",
                message = "Authenticated UID must not be blank",
                requiresUserIntervention = true
            )
        }

        val docRef = firestore.collection(WASTE_REQUESTS_COLLECTION).document(requestId)

        return try {
            val snapshot = docRef.get().await()
            if (!snapshot.exists()) {
                return RemoteRequestOutcome.Rejected(
                    errorCode = "NOT_FOUND",
                    message = "Request document '$requestId' does not exist in Firestore",
                    requiresUserIntervention = false
                )
            }
            val dto = snapshot.toObject(WasteRequestDto::class.java)
                ?: return RemoteRequestOutcome.Rejected(
                    errorCode = "MALFORMED_REMOTE_DOC",
                    message = "Remote document could not be parsed."
                )

            if (dto.clientUid != authenticatedUid) {
                return RemoteRequestOutcome.Rejected(
                    errorCode = "PERMISSION_DENIED",
                    message = "Access denied: request clientUid does not match authenticated user.",
                    requiresUserIntervention = true
                )
            }

            val domainRequest = WasteRequestRemoteMapper.toDomain(dto, fallbackServiceZoneId = "remote_zone")
            val serverCreatedAt = dto.createdAt?.toDate()?.time ?: System.currentTimeMillis()
            val serverUpdatedAt = dto.updatedAt?.toDate()?.time ?: System.currentTimeMillis()

            RemoteRequestOutcome.AlreadyExists(
                existingRequest = domainRequest,
                requestRef = dto.requestRef,
                serverCreatedAtEpochMs = serverCreatedAt,
                serverUpdatedAtEpochMs = serverUpdatedAt
            )
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            mapException(e)
        }
    }

    private fun mapException(throwable: Throwable): RemoteRequestOutcome {
        return when (throwable) {
            is FirebaseFirestoreException -> {
                when (throwable.code) {
                    FirebaseFirestoreException.Code.PERMISSION_DENIED ->
                        RemoteRequestOutcome.Rejected(
                            errorCode = "PERMISSION_DENIED",
                            message = throwable.message ?: "Permission denied by Firestore security rules",
                            requiresUserIntervention = true
                        )
                    FirebaseFirestoreException.Code.UNAUTHENTICATED ->
                        RemoteRequestOutcome.Rejected(
                            errorCode = "UNAUTHENTICATED",
                            message = "User session is unauthenticated or token revoked",
                            requiresUserIntervention = true
                        )
                    FirebaseFirestoreException.Code.UNAVAILABLE,
                    FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
                    FirebaseFirestoreException.Code.ABORTED,
                    FirebaseFirestoreException.Code.INTERNAL,
                    FirebaseFirestoreException.Code.RESOURCE_EXHAUSTED ->
                        RemoteRequestOutcome.TransientFailure(
                            cause = throwable,
                            message = throwable.message ?: "Transient Firestore error (${throwable.code})"
                        )
                    else ->
                        RemoteRequestOutcome.TransientFailure(
                            cause = throwable,
                            message = throwable.message ?: "Firestore error (${throwable.code})"
                        )
                }
            }
            is IOException,
            is TimeoutException ->
                RemoteRequestOutcome.TransientFailure(
                    cause = throwable,
                    message = throwable.message ?: "Network error communicating with Firestore"
                )
            else ->
                RemoteRequestOutcome.TransientFailure(
                    cause = throwable,
                    message = throwable.message ?: "Unexpected error during Firestore communication"
                )
        }
    }
}
