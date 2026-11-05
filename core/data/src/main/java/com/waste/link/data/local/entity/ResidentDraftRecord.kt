package com.waste.link.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room persistence entity storing an incomplete resident wizard draft.
 *
 * Strict Architectural Invariants:
 * 1. Scoped strictly to [clientUid] as the primary key. Exactly one active draft exists per authenticated UID.
 * 2. Explicit typed columns are used for all wizard fields to enable atomic recovery and indexation.
 * 3. Draft timestamps are local device metadata, not server-authoritative timestamps.
 * 4. [gatePhotoLocalPath] stores a local file path or URI reference to avoid SQLite cursor bloat.
 */
@Entity(tableName = "resident_drafts")
data class ResidentDraftRecord(
    @PrimaryKey
    val clientUid: String,
    val clientGeneratedId: String,
    val currentStep: String,
    val categoryId: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val pickupAddress: String? = null,
    val serviceZoneId: String? = null,
    val notes: String? = null,
    val gatePhotoLocalPath: String? = null,
    val volumeTierId: String? = null,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long
)
