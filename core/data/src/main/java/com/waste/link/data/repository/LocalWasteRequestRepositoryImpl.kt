package com.waste.link.data.repository

import androidx.room.withTransaction
import com.waste.link.data.local.WasteLinkDatabase
import com.waste.link.data.local.dao.LocalWasteRequestDao
import com.waste.link.data.local.dao.ResidentDraftDao
import com.waste.link.data.local.entity.LocalWasteRequestRecord
import com.waste.link.data.local.model.LocalSyncStatus
import com.waste.link.data.mapper.toDomain
import com.waste.link.data.mapper.toLocalRecord
import com.waste.link.data.mapper.toRecord
import com.waste.link.data.models.ResidentDraft
import com.waste.link.domain.model.WasteRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Concrete implementation of [LocalWasteRequestRepository] backed by [WasteLinkDatabase].
 *
 * Enforces strict user isolation across all operations.
 */
@Singleton
class LocalWasteRequestRepositoryImpl @Inject constructor(
    private val database: WasteLinkDatabase,
    private val draftDao: ResidentDraftDao,
    private val requestDao: LocalWasteRequestDao
) : LocalWasteRequestRepository {

    // =========================================================================
    // DRAFT OPERATIONS
    // =========================================================================

    override suspend fun saveDraft(clientUid: String, draft: ResidentDraft) {
        validateUid(clientUid)
        require(draft.clientUid == clientUid) {
            "Draft clientUid ('${draft.clientUid}') does not match authenticated user ('$clientUid')."
        }
        draftDao.saveDraft(draft.toRecord())
    }

    override suspend fun getDraft(clientUid: String): ResidentDraft? {
        validateUid(clientUid)
        return draftDao.getDraft(clientUid)?.toDomain()
    }

    override fun observeDraft(clientUid: String): Flow<ResidentDraft?> {
        validateUid(clientUid)
        return draftDao.observeDraft(clientUid).map { it?.toDomain() }
    }

    override suspend fun deleteDraft(clientUid: String): Boolean {
        validateUid(clientUid)
        return draftDao.deleteDraft(clientUid) > 0
    }

    // =========================================================================
    // LOCAL REQUEST OPERATIONS
    // =========================================================================

    override suspend fun saveLocalRequest(
        authenticatedUid: String,
        request: WasteRequest,
        syncStatus: LocalSyncStatus,
        gatePhotoLocalPath: String?
    ): WasteRequest {
        validateUid(authenticatedUid)
        val normalizedAuthUid = authenticatedUid.trim()
        require(request.clientUid == normalizedAuthUid) {
            "Request clientUid ('${request.clientUid}') does not match authenticated user ('$authenticatedUid'). " +
                "Caller cannot persist a request carrying another user's UID."
        }
        val record = request.toLocalRecord(
            syncStatus = syncStatus,
            gatePhotoLocalPath = gatePhotoLocalPath
        )
        requestDao.insertRequest(record)
        return request
    }

    override suspend fun getLocalRequest(id: String, clientUid: String): WasteRequest? {
        validateUid(clientUid)
        require(id.isNotBlank()) { "Request ID must not be blank" }
        return requestDao.getRequestById(id.trim(), clientUid.trim())?.toDomain()
    }

    override suspend fun getLocalRecord(id: String, clientUid: String): LocalWasteRequestRecord? {
        validateUid(clientUid)
        require(id.isNotBlank()) { "Request ID must not be blank" }
        return requestDao.getRequestById(id.trim(), clientUid.trim())
    }

    override suspend fun getLocalRequests(clientUid: String): List<WasteRequest> {
        validateUid(clientUid)
        return requestDao.getRequestsByUser(clientUid.trim()).map { it.toDomain() }
    }

    override fun observeLocalRequests(clientUid: String): Flow<List<WasteRequest>> {
        validateUid(clientUid)
        return requestDao.observeRequestsByUser(clientUid.trim()).map { list ->
            list.map { it.toDomain() }
        }
    }

    override suspend fun getQueuedRequests(clientUid: String): List<WasteRequest> {
        validateUid(clientUid)
        return requestDao.getRequestsBySyncStatus(
            clientUid = clientUid.trim(),
            syncStatus = LocalSyncStatus.QUEUED_OFFLINE
        ).map { it.toDomain() }
    }

    override suspend fun updateSyncStatus(
        id: String,
        clientUid: String,
        newStatus: LocalSyncStatus,
        errorMessage: String?,
        attemptEpochMs: Long?,
        retryCount: Int?
    ): Boolean {
        validateUid(clientUid)
        require(id.isNotBlank()) { "Request ID must not be blank" }

        val existing = requestDao.getRequestById(id.trim(), clientUid.trim()) ?: return false
        val effectiveRetryCount = retryCount ?: existing.retryCount
        val effectiveAttemptMs = attemptEpochMs ?: existing.lastSyncAttemptEpochMs

        val rowsUpdated = requestDao.updateSyncStatus(
            id = id.trim(),
            clientUid = clientUid.trim(),
            syncStatus = newStatus,
            lastSyncAttemptEpochMs = effectiveAttemptMs,
            errorMessage = errorMessage,
            retryCount = effectiveRetryCount
        )
        return rowsUpdated > 0
    }

    override suspend fun deleteLocalRequest(id: String, clientUid: String): Boolean {
        validateUid(clientUid)
        require(id.isNotBlank()) { "Request ID must not be blank" }
        return requestDao.deleteRequest(id.trim(), clientUid.trim()) > 0
    }

    override suspend fun <T> runInTransaction(block: suspend () -> T): T {
        return database.withTransaction {
            block()
        }
    }

    private fun validateUid(clientUid: String) {
        require(clientUid.isNotBlank()) { "clientUid must not be blank" }
    }
}
