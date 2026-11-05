package com.waste.link.data.repository

import com.waste.link.data.local.entity.LocalWasteRequestRecord
import com.waste.link.data.local.model.LocalSyncStatus
import com.waste.link.data.models.ResidentDraft
import com.waste.link.domain.model.WasteRequest
import kotlinx.coroutines.flow.Flow

/**
 * Repository interface for local client persistence, resident wizard draft retention,
 * and offline request queuing.
 *
 * Strict Architectural Invariants:
 * 1. Data-layer boundary: Belongs strictly to `:core:data`.
 * 2. Mandatory UID isolation: All operations are scoped to an authenticated user UID.
 * 3. Never disguised as remote submission: Does NOT implement or return
 *    `CreateWasteRequestOutcome.Success`.
 * 4. Sync state separation: [LocalSyncStatus] is maintained independently of domain
 *    `RequestLifecycleStatus`.
 */
interface LocalWasteRequestRepository {

    // =========================================================================
    // DRAFT OPERATIONS
    // =========================================================================

    /**
     * Persists or updates the current user's in-progress wizard draft.
     * Fails safely if [clientUid] is blank or does not match [draft.clientUid].
     */
    suspend fun saveDraft(clientUid: String, draft: ResidentDraft)

    /**
     * Retrieves the current user's draft, or null if no draft exists.
     * Never returns another user's draft.
     */
    suspend fun getDraft(clientUid: String): ResidentDraft?

    /**
     * Observes the current user's draft.
     */
    fun observeDraft(clientUid: String): Flow<ResidentDraft?>

    /**
     * Deletes the current user's draft.
     * Returns true if a draft was deleted, false otherwise.
     */
    suspend fun deleteDraft(clientUid: String): Boolean

    // =========================================================================
    // LOCAL REQUEST OPERATIONS
    // =========================================================================

    /**
     * Inserts a validated [WasteRequest] into local persistence with an initial [syncStatus].
     *
     * Strict Identity & Ownership Invariant:
     * [authenticatedUid] must be non-blank and must match [request.clientUid] exactly.
     * Mismatches are rejected immediately with [IllegalArgumentException] before any write to persistence.
     *
     * Fails deterministically on duplicate (id, clientUid).
     */
    suspend fun saveLocalRequest(
        authenticatedUid: String,
        request: WasteRequest,
        syncStatus: LocalSyncStatus = LocalSyncStatus.QUEUED_OFFLINE,
        gatePhotoLocalPath: String? = null
    ): WasteRequest

    /**
     * Retrieves a local request by its client-generated [id] and [clientUid].
     * Returns null if not found or if the record belongs to another user.
     */
    suspend fun getLocalRequest(id: String, clientUid: String): WasteRequest?

    /**
     * Retrieves the full local persistence record including synchronization metadata.
     */
    suspend fun getLocalRecord(id: String, clientUid: String): LocalWasteRequestRecord?

    /**
     * Retrieves all local requests belonging to the given [clientUid].
     */
    suspend fun getLocalRequests(clientUid: String): List<WasteRequest>

    /**
     * Observes all local requests belonging to the given [clientUid].
     */
    fun observeLocalRequests(clientUid: String): Flow<List<WasteRequest>>

    /**
     * Retrieves all requests in [QUEUED_OFFLINE] state for the given [clientUid].
     */
    suspend fun getQueuedRequests(clientUid: String): List<WasteRequest>

    /**
     * Updates the [LocalSyncStatus] and sync metadata for a specific request owned by [clientUid].
     * Returns true if the record was found and updated, false otherwise.
     */
    suspend fun updateSyncStatus(
        id: String,
        clientUid: String,
        newStatus: LocalSyncStatus,
        errorMessage: String? = null,
        attemptEpochMs: Long? = null,
        retryCount: Int? = null
    ): Boolean

    /**
     * Deletes a local request owned by [clientUid].
     * Returns true if deleted, false otherwise.
     */
    suspend fun deleteLocalRequest(id: String, clientUid: String): Boolean

    /**
     * Executes multiple database writes atomically in a single transaction.
     */
    suspend fun <T> runInTransaction(block: suspend () -> T): T
}
