package com.waste.link.domain.repository

import com.waste.link.domain.model.DynamicWasteCategory
import com.waste.link.domain.model.ServiceZone
import com.waste.link.domain.model.WasteRequest
import kotlinx.coroutines.flow.Flow

/**
 * Structured outcome of an authenticated waste request creation operation.
 */
sealed interface WasteRequestCreationOutcome {
    /**
     * Request successfully committed and confirmed by the trusted server authority.
     * Contains the authoritative [WasteRequest] with server-allocated `requestRef` and timestamps.
     */
    data class Success(val request: WasteRequest) : WasteRequestCreationOutcome

    /**
     * Network unavailable; request saved to local persistence and queued for background synchronization.
     */
    data class QueuedOffline(val clientGeneratedId: String) : WasteRequestCreationOutcome

    /**
     * Creation rejected by server validation, security rules, cooldown rate limit, or network error.
     */
    data class Failure(val error: Throwable, val message: String = error.message ?: "Failed to create waste request.") : WasteRequestCreationOutcome
}

/**
 * Structured outcome of a request cancellation attempt.
 */
sealed interface WasteRequestCancellationOutcome {
    data object Success : WasteRequestCancellationOutcome
    data class Failure(val error: Throwable, val message: String = error.message ?: "Failed to cancel waste request.") : WasteRequestCancellationOutcome
}

/**
 * Authoritative domain contract for Waste Request operations.
 *
 * Strict Architectural Invariant:
 * Request creation is defined as an authenticated trusted operation named [createWasteRequest],
 * rather than assuming a direct unmediated Firestore client write. This encapsulates server-side
 * rate limiting, municipal zone validation, reference allocation, and multi-layer duplicate prevention.
 */
interface WasteRequestRepository {

    /**
     * Authenticated trusted operation that submits a waste collection request.
     *
     * @param request The domain entity containing validated draft inputs.
     * @return [WasteRequestCreationOutcome] reflecting server confirmation, offline queuing, or failure.
     */
    suspend fun createWasteRequest(request: WasteRequest): WasteRequestCreationOutcome

    /**
     * Cancels an existing request that is still in `PENDING_DISPATCH`.
     *
     * @param requestId The deterministic document ID / clientGeneratedId.
     * @param actorUid The UID of the authenticated resident requesting cancellation.
     */
    suspend fun cancelWasteRequest(requestId: String, actorUid: String): WasteRequestCancellationOutcome

    /**
     * Observes live operational updates for a single request via reactive flow.
     */
    fun watchWasteRequest(requestId: String): Flow<WasteRequest?>

    /**
     * Observes all active and historical requests belonging to [clientUid].
     */
    fun watchResidentRequests(clientUid: String): Flow<List<WasteRequest>>
}

/**
 * Domain contract for discovering remote configurable waste categories.
 */
interface WasteCategoryRepository {
    /** Observes the active category catalog dynamically. */
    fun watchActiveCategories(): Flow<List<DynamicWasteCategory>>

    /** Retrieves the current cached or remote categories snapshot. */
    suspend fun getCategories(): List<DynamicWasteCategory>
}

/**
 * Domain contract for discovering municipal service zones.
 */
interface ServiceZoneRepository {
    /** Observes active service zone boundaries dynamically. */
    fun watchActiveServiceZones(): Flow<List<ServiceZone>>

    /** Retrieves active municipal zones snapshot. */
    suspend fun getActiveServiceZones(): List<ServiceZone>
}
