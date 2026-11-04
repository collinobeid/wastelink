package com.waste.link.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "offline_requests")
data class WasteRequestEntity(
    @PrimaryKey
    val requestRef: String,
    val clientUid: String,
    val geohash: String,
    val lat: Double,
    val lng: Double,
    val pickupAddress: String,
    val category: String,
    val volumeTier: String,
    val notes: String,
    val gatePhotoBase64: String?,
    val pocPhotoBase64: String?,
    val status: String,
    val createdAtMillis: Long
)
