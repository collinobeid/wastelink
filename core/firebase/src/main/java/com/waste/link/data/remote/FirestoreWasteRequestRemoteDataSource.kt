package com.waste.link.data.remote

import androidx.annotation.VisibleForTesting
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.waste.link.data.remote.dto.WasteRequestDto
import com.waste.link.data.remote.mapper.WasteRequestRemoteMapper
import com.waste.link.domain.model.RequestRefSpecification
import com.waste.link.domain.model.WasteRequest
import kotlinx.coroutines.tasks.await
import java.io.IOException
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
 * 4. Atomic transaction ensures idempotency: reconciles matching payloads, rejects conflicting payloads.
 * 5. Requires server-authoritative [request.requestRef]; client does not manufacture canonical references.
 * 6. Authoritatively validates category and service zone against Firestore configuration.
 * 7. Writes with [FieldValue.serverTimestamp()] and persists only confirmed server timestamps.
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
        const val WASTE_CATEGORIES_COLLECTION = "waste_categories"
        const val SERVICE_ZONES_COLLECTION = "service_zones"
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

        // Invariant 3: Server-authoritative reference check (Finding 1)
        val requestRef = request.requestRef
        if (requestRef.isNullOrBlank()) {
            return RemoteRequestOutcome.Rejected(
                errorCode = "REFERENCE_ALLOCATION_REQUIRED",
                message = "Server-authoritative reference is required for remote submission. Canonical sequential reference allocation requires a trusted Cloud Function endpoint.",
                requiresUserIntervention = false
            )
        }
        if (!RequestRefSpecification.isValid(requestRef)) {
            return RemoteRequestOutcome.Rejected(
                errorCode = "INVALID_REQUEST_REF",
                message = "Request reference '$requestRef' is invalid according to RequestRefSpecification.",
                requiresUserIntervention = true
            )
        }

        val docRef = firestore.collection(WASTE_REQUESTS_COLLECTION).document(request.id)
        val categoryRef = firestore.collection(WASTE_CATEGORIES_COLLECTION).document(request.categoryId)
        val zoneRef = firestore.collection(SERVICE_ZONES_COLLECTION).document(request.serviceZoneId)

        // Invariant 4 & 5: Atomic transaction creation, duplicate reconciliation, conflict rejection, and category/zone validation
        return try {
            val outcome = firestore.runTransaction { transaction ->
                val snapshot = transaction.get(docRef)
                if (snapshot.exists()) {
                    val dto = snapshot.toObject(WasteRequestDto::class.java)
                        ?: return@runTransaction RemoteRequestOutcome.Rejected(
                            errorCode = "MALFORMED_REMOTE_DOC",
                            message = "Existing remote document could not be parsed.",
                            requiresUserIntervention = true
                        )

                    // Verify ownership
                    if (dto.clientUid != authenticatedUid) {
                        return@runTransaction RemoteRequestOutcome.Rejected(
                            errorCode = "PERMISSION_DENIED",
                            message = "Access denied: existing document clientUid does not match authenticated user.",
                            requiresUserIntervention = true
                        )
                    }

                    // Verify immutable identity and payload consistency
                    val matchesCategory = dto.category == request.categoryId
                    val matchesVolumeTier = dto.volumeTier.equals(request.volumeTier.name, ignoreCase = true) ||
                        dto.volumeTier.equals(request.volumeTier.id, ignoreCase = true)
                    val matchesAddress = dto.pickupAddress == request.pickupAddress
                    val matchesCoords = kotlin.math.abs(dto.lat - request.coordinates.latitude) < 0.00001 &&
                        kotlin.math.abs(dto.lng - request.coordinates.longitude) < 0.00001

                    if (!matchesCategory || !matchesVolumeTier || !matchesAddress || !matchesCoords) {
                        return@runTransaction RemoteRequestOutcome.Rejected(
                            errorCode = "CONFLICTING_EXISTING_DOCUMENT",
                            message = "Remote document '${request.id}' already exists with conflicting payload.",
                            requiresUserIntervention = true
                        )
                    }

                    // Reconcile matching existing request safely
                    val existingDomain = WasteRequestRemoteMapper.toDomain(dto, request.serviceZoneId)
                    val serverCreatedAt = dto.createdAt?.toDate()?.time ?: 0L
                    val serverUpdatedAt = dto.updatedAt?.toDate()?.time ?: serverCreatedAt

                    return@runTransaction RemoteRequestOutcome.AlreadyExists(
                        existingRequest = existingDomain,
                        requestRef = dto.requestRef,
                        serverCreatedAtEpochMs = serverCreatedAt,
                        serverUpdatedAtEpochMs = serverUpdatedAt
                    )
                }

                // If document does not exist, authoritatively validate category and service zone
                val categorySnap = transaction.get(categoryRef)
                if (!categorySnap.exists()) {
                    return@runTransaction RemoteRequestOutcome.Rejected(
                        errorCode = "UNKNOWN_CATEGORY",
                        message = "Waste category '${request.categoryId}' does not exist in authoritative configuration.",
                        requiresUserIntervention = true
                    )
                }
                if (categorySnap.getBoolean("isActive") != true) {
                    return@runTransaction RemoteRequestOutcome.Rejected(
                        errorCode = "INACTIVE_CATEGORY",
                        message = "Waste category '${request.categoryId}' is currently inactive.",
                        requiresUserIntervention = true
                    )
                }

                val zoneSnap = transaction.get(zoneRef)
                if (!zoneSnap.exists()) {
                    return@runTransaction RemoteRequestOutcome.Rejected(
                        errorCode = "UNKNOWN_SERVICE_ZONE",
                        message = "Service zone '${request.serviceZoneId}' does not exist in authoritative configuration.",
                        requiresUserIntervention = true
                    )
                }
                if (zoneSnap.getBoolean("isActive") != true) {
                    return@runTransaction RemoteRequestOutcome.Rejected(
                        errorCode = "INACTIVE_SERVICE_ZONE",
                        message = "Service zone '${request.serviceZoneId}' is currently inactive.",
                        requiresUserIntervention = true
                    )
                }

                val minLat = zoneSnap.getDouble("minLat")
                val maxLat = zoneSnap.getDouble("maxLat")
                val minLng = zoneSnap.getDouble("minLng")
                val maxLng = zoneSnap.getDouble("maxLng")
                if (minLat != null && maxLat != null && minLng != null && maxLng != null) {
                    if (request.coordinates.latitude !in minLat..maxLat || request.coordinates.longitude !in minLng..maxLng) {
                        return@runTransaction RemoteRequestOutcome.Rejected(
                            errorCode = "OUTSIDE_SERVICE_ZONE",
                            message = "Request coordinates (${request.coordinates.latitude}, ${request.coordinates.longitude}) fall outside service zone '${request.serviceZoneId}' boundary.",
                            requiresUserIntervention = true
                        )
                    }
                }

                // Atomic creation with server timestamps
                val dto = WasteRequestRemoteMapper.toDto(request, allocatedRef = requestRef)
                val creationMap = dto.toFirestoreCreationMap(
                    createdAtValue = FieldValue.serverTimestamp(),
                    updatedAtValue = FieldValue.serverTimestamp()
                )
                transaction.set(docRef, creationMap)

                RemoteRequestOutcome.Success(
                    confirmedRequest = request.copy(requestRef = requestRef),
                    requestRef = requestRef,
                    serverCreatedAtEpochMs = 0L,
                    serverUpdatedAtEpochMs = 0L
                )
            }.await()

            if (outcome is RemoteRequestOutcome.Success) {
                // Retrieve the committed document to get the server-resolved timestamps
                val committedSnapshot = docRef.get().await()
                val committedDto = committedSnapshot.toObject(WasteRequestDto::class.java)
                val resolvedCreatedAt = committedDto?.createdAt?.toDate()?.time ?: 0L
                val resolvedUpdatedAt = committedDto?.updatedAt?.toDate()?.time ?: resolvedCreatedAt
                val fullyConfirmed = request.copy(
                    requestRef = outcome.requestRef,
                    createdAtEpochMs = resolvedCreatedAt,
                    updatedAtEpochMs = resolvedUpdatedAt
                )
                RemoteRequestOutcome.Success(
                    confirmedRequest = fullyConfirmed,
                    requestRef = outcome.requestRef,
                    serverCreatedAtEpochMs = resolvedCreatedAt,
                    serverUpdatedAtEpochMs = resolvedUpdatedAt
                )
            } else {
                outcome
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            mapException(e)
        }
    }

    override suspend fun getRequestById(
        authenticatedUid: String,
        requestId: String,
        serviceZoneId: String?
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

            if (serviceZoneId.isNullOrBlank() || serviceZoneId == "remote_zone") {
                return RemoteRequestOutcome.Rejected(
                    errorCode = "SERVICE_ZONE_REQUIRED",
                    message = "Cannot map remote document without valid serviceZoneId. Service zones are not fabricated.",
                    requiresUserIntervention = true
                )
            }

            val domainRequest = WasteRequestRemoteMapper.toDomain(dto, serviceZoneId = serviceZoneId)
            val serverCreatedAt = dto.createdAt?.toDate()?.time ?: 0L
            val serverUpdatedAt = dto.updatedAt?.toDate()?.time ?: serverCreatedAt

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
