package com.waste.link.data.models

/**
 * Data-layer representation of an in-progress resident request wizard draft.
 *
 * Scoped to the authenticated resident's [clientUid]. Preserves wizard inputs across
 * configuration changes, background kills, and process restarts.
 *
 * Invariant:
 * [gatePhotoLocalPath] stores a local file path or URI reference. Large Base64 payloads
 * are strictly prohibited from database storage. The referenced photo file is local-device
 * state and not durable across app uninstalls or cache purges.
 */
data class ResidentDraft(
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
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val updatedAtEpochMs: Long = System.currentTimeMillis()
) {
    init {
        require(clientUid.isNotBlank()) { "ResidentDraft.clientUid must not be blank" }
        require(clientGeneratedId.isNotBlank()) { "ResidentDraft.clientGeneratedId must not be blank" }
        require(currentStep.isNotBlank()) { "ResidentDraft.currentStep must not be blank" }
    }
}
