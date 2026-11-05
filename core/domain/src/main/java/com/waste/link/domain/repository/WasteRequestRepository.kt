package com.waste.link.domain.repository

import com.waste.link.domain.model.WasteRequest

/**
 * Domain outcome of an authenticated trusted request creation operation.
 *
 * Strict Architectural Invariant:
 * Local Room synchronization states (LOCAL_DRAFT, QUEUED_OFFLINE, UPLOADING, SYNCED)
 * belong strictly in the data layer (LocalSyncStatus) and are NOT exposed in this domain contract.
 */
sealed interface CreateWasteRequestOutcome {
    /**
     * Creation confirmed by the trusted backend authority.
     * Contains the authoritative [WasteRequest] with server-allocated request reference and timestamps.
     */
    data class Success(val confirmedRequest: WasteRequest) : CreateWasteRequestOutcome

    /**
     * Creation rejected by server validation, security rules, cooldown rate limit, or network error.
     */
    data class Failure(
        val error: Throwable,
        val message: String = error.message ?: "Failed to create waste request."
    ) : CreateWasteRequestOutcome
}

/**
 * Domain contract for submitting waste collection requests.
 *
 * Strict Architectural Invariant:
 * Request creation is defined as an authenticated trusted operation named [createWasteRequest],
 * rather than assuming a direct unmediated Firestore client write.
 */
interface WasteRequestRepository {

    /**
     * Authenticated trusted operation that submits a validated waste collection request.
     *
     * @param request The domain entity containing validated inputs.
     * @return [CreateWasteRequestOutcome] representing server confirmation or failure.
     */
    suspend fun createWasteRequest(request: WasteRequest): CreateWasteRequestOutcome
}
