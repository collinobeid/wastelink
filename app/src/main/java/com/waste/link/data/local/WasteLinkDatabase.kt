package com.waste.link.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [WasteRequestEntity::class],
    version = 1,
    exportSchema = false
)
abstract class WasteLinkDatabase : RoomDatabase() {
    abstract val wasteRequestDao: WasteRequestDao
    
    companion object {
        const val DATABASE_NAME = "wastelink_db"
    }
}
