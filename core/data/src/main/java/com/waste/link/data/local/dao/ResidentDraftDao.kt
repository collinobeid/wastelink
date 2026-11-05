package com.waste.link.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.waste.link.data.local.entity.ResidentDraftRecord
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for resident wizard drafts.
 *
 * Strict UID Isolation:
 * All queries, updates, and deletions MUST be explicitly scoped by [clientUid].
 */
@Dao
interface ResidentDraftDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveDraft(draft: ResidentDraftRecord): Long

    @Query("SELECT * FROM resident_drafts WHERE clientUid = :clientUid LIMIT 1")
    suspend fun getDraft(clientUid: String): ResidentDraftRecord?

    @Query("SELECT * FROM resident_drafts WHERE clientUid = :clientUid LIMIT 1")
    fun observeDraft(clientUid: String): Flow<ResidentDraftRecord?>

    @Query("DELETE FROM resident_drafts WHERE clientUid = :clientUid")
    suspend fun deleteDraft(clientUid: String): Int
}
