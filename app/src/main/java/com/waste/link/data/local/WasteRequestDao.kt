package com.waste.link.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface WasteRequestDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRequest(request: WasteRequestEntity)

    @Query("SELECT * FROM offline_requests ORDER BY createdAtMillis DESC")
    fun getAllOfflineRequests(): Flow<List<WasteRequestEntity>>

    @Query("SELECT * FROM offline_requests")
    suspend fun getOfflineRequestsSync(): List<WasteRequestEntity>

    @Query("DELETE FROM offline_requests WHERE requestRef = :requestRef")
    suspend fun deleteRequest(requestRef: String)
}
