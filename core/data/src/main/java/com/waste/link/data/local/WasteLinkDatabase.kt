package com.waste.link.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.waste.link.data.local.dao.LocalWasteRequestDao
import com.waste.link.data.local.dao.ResidentDraftDao
import com.waste.link.data.local.entity.LocalWasteRequestRecord
import com.waste.link.data.local.entity.ResidentDraftRecord

/**
 * Authoritative Room database for WasteLink local client persistence.
 *
 * Encapsulates offline request queuing, resident wizard draft retention, and synchronization metadata.
 */
@Database(
    entities = [
        LocalWasteRequestRecord::class,
        ResidentDraftRecord::class
    ],
    version = 2,
    exportSchema = true
)
@TypeConverters(WasteLinkTypeConverters::class)
abstract class WasteLinkDatabase : RoomDatabase() {

    abstract fun localWasteRequestDao(): LocalWasteRequestDao

    abstract fun residentDraftDao(): ResidentDraftDao

    companion object {
        const val DATABASE_NAME = "wastelink_db"
    }
}
