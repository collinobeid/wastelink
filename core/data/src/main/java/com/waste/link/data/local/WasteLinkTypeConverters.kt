package com.waste.link.data.local

import androidx.room.TypeConverter
import com.waste.link.data.local.model.LocalSyncStatus

/**
 * Room TypeConverters for local persistence.
 */
class WasteLinkTypeConverters {

    @TypeConverter
    fun fromLocalSyncStatus(status: LocalSyncStatus?): String? = status?.name

    @TypeConverter
    fun toLocalSyncStatus(value: String?): LocalSyncStatus? {
        if (value.isNullOrBlank()) return null
        return runCatching { LocalSyncStatus.valueOf(value.trim().uppercase()) }.getOrNull()
    }
}
