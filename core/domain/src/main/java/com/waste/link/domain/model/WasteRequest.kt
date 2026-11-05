package com.waste.link.domain.model

/**
 * Pure Kotlin business-domain entity representing a WasteLink waste collection request.
 *
 * Strict Architectural Invariants:
 * 1. ZERO Android SDK imports, ZERO Room annotations, ZERO Firebase annotations.
 * 2. ZERO local synchronization metadata: Local Room synchronization states (`LOCAL_DRAFT`,
 *    `QUEUED_OFFLINE`, `UPLOADING`, `SYNCED`, `SYNC_FAILED`) are strictly encapsulated in
 *    `LocalWasteRequestRecord` within `:core:data`.
 * 3. [id] matches [clientGeneratedId] exactly (deterministic UUID idempotency key).
 * 4. [requestRef] represents an unallocated server reference when null, until returned by the backend.
 * 5. Timestamps default to null; client does NOT fabricate authoritative timestamps with `System.currentTimeMillis()`.
 * 6. Monetary amounts are integer units in [currency] ("TZS").
 * 7. [geohash] must strictly match the derived geohash from [coordinates]; silent disagreement is prohibited.
 */
data class WasteRequest(
    val id: String,
    val clientGeneratedId: String,
    val requestRef: String? = null,
    val clientUid: String,
    val coordinates: GeoCoordinates,
    val geohash: String = coordinates.toGeohash(),
    val serviceZoneId: String,
    val pickupAddress: String,
    val categoryId: String,
    val volumeTier: VolumeTier,
    val notes: String = "",
    val gatePhotoUrl: String? = null,
    val gatePhotoBase64: String? = null,
    val pocPhotoUrl: String? = null,
    val pocPhotoBase64: String? = null,
    val status: RequestLifecycleStatus = RequestLifecycleStatus.PENDING_DISPATCH,
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
    val failedAtEpochMs: Long? = null
) {
    init {
        require(id.isNotBlank()) { "WasteRequest.id must not be blank" }
        require(clientGeneratedId.isNotBlank()) { "WasteRequest.clientGeneratedId must not be blank" }
        require(id == clientGeneratedId) {
            "WasteRequest.id ($id) must match clientGeneratedId ($clientGeneratedId) exactly."
        }
        require(clientUid.isNotBlank()) { "WasteRequest.clientUid must not be blank" }
        require(serviceZoneId.isNotBlank()) { "WasteRequest.serviceZoneId must not be blank" }
        require(pickupAddress.isNotBlank()) { "WasteRequest.pickupAddress must not be blank" }
        require(categoryId.isNotBlank()) { "WasteRequest.categoryId must not be blank" }
        require(currency == "TZS") { "Currency must be 'TZS', received '$currency'" }
        require(geohash.length in 5..12) { "geohash length must be between 5 and 12, received '$geohash'" }
        require(geohash == coordinates.toGeohash(geohash.length)) {
            "Supplied geohash '$geohash' does not match coordinates ($coordinates)."
        }
        if (priceTzs != null) {
            require(priceTzs > 0L) { "priceTzs must be strictly positive, received $priceTzs" }
        }
        if (requestRef != null) {
            require(RequestRefSpecification.isValid(requestRef)) {
                "Invalid requestRef format: '$requestRef'. Must match ^WL-[0-9]{4}-[0-9A-Z]{6}$"
            }
        }
    }

    /**
     * True if photographic Proof of Collection (PoC) has been attached by the driver.
     */
    val hasProofOfCollection: Boolean
        get() = !pocPhotoUrl.isNullOrBlank() || !pocPhotoBase64.isNullOrBlank()

    /**
     * True if failure evidence (both reason string and photo evidence) is present.
     */
    val hasFailureEvidence: Boolean
        get() = (!failurePhotoUrl.isNullOrBlank() || !failurePhotoBase64.isNullOrBlank()) &&
            !failureReason.isNullOrBlank()

    /**
     * True if this request has been assigned to a collector.
     */
    val isAssigned: Boolean
        get() = !assignedCollectorUid.isNullOrBlank()

    /**
     * True if the human-readable reference has not yet been allocated by the server.
     */
    val isUnallocatedRef: Boolean
        get() = requestRef == null
}
