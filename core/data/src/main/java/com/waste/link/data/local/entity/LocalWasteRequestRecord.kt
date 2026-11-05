package com.waste.link.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import com.waste.link.data.local.model.LocalSyncStatus

/**
 * Room persistence entity representing a local validated waste collection request.
 *
 * Strict Architectural Invariants:
 * 1. Encapsulated in `:core:data`. Does NOT replace or mutate the pure domain `WasteRequest`.
 * 2. Uniquely identified by composite primary key (`id`, `clientUid`) to guarantee that
 *    records are scoped to the authenticated owner and prevent cross-UID overwrite.
 * 3. [syncStatus] represents local synchronization progress (`LOCAL_DRAFT`, `QUEUED_OFFLINE`,
 *    `UPLOADING`, `SYNCED`, `SYNC_FAILED`) and must never be conflated with operational [status].
 * 4. Local device timestamps are local metadata and never treated as server-authoritative.
 * 5. [gatePhotoLocalPath] stores the local file path or URI reference to avoid SQLite cursor bloat.
 */
@Entity(
    tableName = "local_waste_requests",
    primaryKeys = ["id", "clientUid"],
    indices = [
        Index(value = ["clientUid"]),
        Index(value = ["clientUid", "syncStatus"]),
        Index(value = ["clientUid", "createdAtEpochMs"])
    ]
)
data class LocalWasteRequestRecord(
    val id: String,
    val clientGeneratedId: String,
    val clientUid: String,
    val requestRef: String? = null,
    val latitude: Double,
    val longitude: Double,
    val geohash: String,
    val serviceZoneId: String,
    val pickupAddress: String,
    val categoryId: String,
    val volumeTierId: String,
    val notes: String = "",
    val gatePhotoUrl: String? = null,
    val gatePhotoLocalPath: String? = null,
    val gatePhotoBase64: String? = null,
    val pocPhotoUrl: String? = null,
    val pocPhotoBase64: String? = null,
    val status: String,
    val syncStatus: LocalSyncStatus,
    val assignedCollectorUid: String? = null,
    val assignedVehicleId: String? = null,
    val priceTzs: Long? = null,
    val currency: String = "TZS",
    val residentPhoneNumber: String? = null,
    val collectorPhoneNumber: String? = null,
    val failureReason: String? = null,
    val failurePhotoUrl: String? = null,
    val failurePhotoBase64: String? = null,
    val disputeReason: String? = null,
    val adminResolutionNote: String? = null,
    val createdAtEpochMs: Long? = null,
    val updatedAtEpochMs: Long? = null,
    val assignedAtEpochMs: Long? = null,
    val enRouteAtEpochMs: Long? = null,
    val arrivedAtEpochMs: Long? = null,
    val collectedAtEpochMs: Long? = null,
    val completedAtEpochMs: Long? = null,
    val failedAtEpochMs: Long? = null,
    val lastSyncAttemptEpochMs: Long? = null,
    val syncErrorMessage: String? = null,
    val retryCount: Int = 0
)
