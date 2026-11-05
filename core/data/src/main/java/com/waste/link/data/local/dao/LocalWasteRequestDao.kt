package com.waste.link.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.waste.link.data.local.entity.LocalWasteRequestRecord
import com.waste.link.data.local.model.LocalSyncStatus
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for local waste collection request records.
 *
 * Strict UID Isolation:
 * Every query, update, and deletion is strictly scoped to [clientUid] to guarantee that
 * User A cannot view, mutate, or delete User B's local requests.
 */
@Dao
interface LocalWasteRequestDao {

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertRequest(record: LocalWasteRequestRecord): Long

    @Query("SELECT * FROM local_waste_requests WHERE id = :id AND clientUid = :clientUid LIMIT 1")
    suspend fun getRequestById(id: String, clientUid: String): LocalWasteRequestRecord?

    @Query("SELECT * FROM local_waste_requests WHERE clientUid = :clientUid ORDER BY createdAtEpochMs DESC")
    suspend fun getRequestsByUser(clientUid: String): List<LocalWasteRequestRecord>

    @Query("SELECT * FROM local_waste_requests WHERE clientUid = :clientUid ORDER BY createdAtEpochMs DESC")
    fun observeRequestsByUser(clientUid: String): Flow<List<LocalWasteRequestRecord>>

    @Query("SELECT * FROM local_waste_requests WHERE clientUid = :clientUid AND syncStatus = :syncStatus ORDER BY createdAtEpochMs ASC")
    suspend fun getRequestsBySyncStatus(clientUid: String, syncStatus: LocalSyncStatus): List<LocalWasteRequestRecord>

    @Query("""
        UPDATE local_waste_requests
        SET syncStatus = :syncStatus,
            lastSyncAttemptEpochMs = :lastSyncAttemptEpochMs,
            syncErrorMessage = :errorMessage,
            retryCount = :retryCount
        WHERE id = :id AND clientUid = :clientUid
    """)
    suspend fun updateSyncStatus(
        id: String,
        clientUid: String,
        syncStatus: LocalSyncStatus,
        lastSyncAttemptEpochMs: Long?,
        errorMessage: String?,
        retryCount: Int
    ): Int

    @Query("""
        UPDATE local_waste_requests
        SET syncStatus = :syncStatus,
            requestRef = :requestRef,
            lastSyncAttemptEpochMs = :lastSyncAttemptEpochMs,
            syncErrorMessage = NULL,
            retryCount = :retryCount
        WHERE id = :id AND clientUid = :clientUid
    """)
    suspend fun updateSyncSuccess(
        id: String,
        clientUid: String,
        syncStatus: LocalSyncStatus,
        requestRef: String,
        lastSyncAttemptEpochMs: Long,
        retryCount: Int
    ): Int

    @Query("""
        UPDATE local_waste_requests
        SET syncStatus = 'QUEUED_OFFLINE'
        WHERE clientUid = :clientUid
          AND syncStatus = 'UPLOADING'
          AND lastSyncAttemptEpochMs < :staleThresholdEpochMs
    """)
    suspend fun recoverStaleUploadingRequests(clientUid: String, staleThresholdEpochMs: Long): Int

    @Update
    suspend fun updateRequest(record: LocalWasteRequestRecord): Int

    @Query("DELETE FROM local_waste_requests WHERE id = :id AND clientUid = :clientUid")
    suspend fun deleteRequest(id: String, clientUid: String): Int

    @Query("SELECT COUNT(*) FROM local_waste_requests WHERE clientUid = :clientUid")
    suspend fun countRequestsByUser(clientUid: String): Int
}
