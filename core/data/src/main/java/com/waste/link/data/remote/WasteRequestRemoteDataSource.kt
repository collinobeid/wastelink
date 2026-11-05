package com.waste.link.data.remote

import com.waste.link.domain.model.WasteRequest

/**
 * Contract for remote waste request operations against the WasteLink backend authority.
 *
 * Strict Identity & Idempotency Invariants:
 * 1. [authenticatedUid] must be verified against current session and must match [request.clientUid].
 * 2. Remote target is uniquely keyed by [request.clientGeneratedId] (UUID v4).
 * 3. Never creates duplicate requests across retries.
 * 4. Reconciles existing records safely through [RemoteRequestOutcome.AlreadyExists].
 */
interface WasteRequestRemoteDataSource {

    /**
     * Submits a validated [request] to Cloud Firestore.
     *
     * @param authenticatedUid The current caller's verified UID.
     * @param request The domain request to submit.
     * @return [RemoteRequestOutcome] representing success, idempotent deduplication, rejection, or transient failure.
     */
    suspend fun createRequest(
        authenticatedUid: String,
        request: WasteRequest
    ): RemoteRequestOutcome

    /**
     * Retrieves an existing remote request by ID to reconcile state or verify prior write.
     *
     * @param authenticatedUid The current caller's verified UID.
     * @param requestId The client-generated request UUID.
     * @return [RemoteRequestOutcome] representing existing document data, rejection, or transient failure.
     */
    suspend fun getRequestById(
        authenticatedUid: String,
        requestId: String
    ): RemoteRequestOutcome
}
