package com.waste.link.data.remote.dto

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.ServerTimestamp

/**
 * Data Transfer Object representing a waste collection request document in Cloud Firestore
 * at path `/waste_requests/{requestId}`.
 *
 * Strict Firestore Security Rules Invariants:
 * 1. Allowed keys are strictly governed by `hasAllowedWasteRequestKeys(data)` in [firestore.rules].
 * 2. `id` must be empty or match document ID `requestId`.
 * 3. `clientUid` must match authenticated caller (`request.auth.uid`).
 * 4. `serviceZoneId` is NEVER written to Firestore (client-side municipal attribute).
 * 5. Initial status is strictly "PENDING_DISPATCH".
 * 6. Assignment and transition fields must be absent or null at creation.
 */
data class WasteRequestDto(
    @DocumentId
    val id: String = "",
    val requestRef: String = "",
    val clientUid: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val geohash: String = "",
    val pickupAddress: String = "",
    val category: String = "",
    val volumeTier: String = "",
    val notes: String = "",
    val gatePhotoBase64: String? = null,
    val pocPhotoBase64: String? = null,
    val gatePhotoUrl: String? = null,
    val pocPhotoUrl: String? = null,
    val photos: List<String>? = null,
    val status: String = "PENDING_DISPATCH",
    val assignedTruckId: String? = null,
    val assignedAt: Timestamp? = null,
    val enRouteAt: Timestamp? = null,
    val arrivedAt: Timestamp? = null,
    val collectedAt: Timestamp? = null,
    val completedAt: Timestamp? = null,
    val failedAt: Timestamp? = null,
    val failureReason: String? = null,
    val agreedPrice: Long? = null,
    val residentPhoneNumber: String? = null,
    val collectorPhoneNumber: String? = null,
    @ServerTimestamp
    val createdAt: Timestamp? = null,
    @ServerTimestamp
    val updatedAt: Timestamp? = null
) {

    /**
     * Converts this DTO into a Map suitable for initial Firestore document creation.
     *
     * Invariants enforced by Firestore security rules `canCreateWasteRequest` and `hasAllowedWasteRequestKeys`:
     * - Only permitted keys in [firestore.rules] lines 591–601 are included.
     * - `id` matches document ID.
     * - Initial status is strictly "PENDING_DISPATCH".
     * - `serviceZoneId` is omitted because it is not an allowed Firestore key.
     * - Assignment and completion fields are omitted or null.
     */
    fun toFirestoreCreationMap(
        createdAtValue: Any = FieldValue.serverTimestamp(),
        updatedAtValue: Any = FieldValue.serverTimestamp()
    ): Map<String, Any?> {
        val map = mutableMapOf<String, Any?>(
            "id" to id,
            "requestRef" to requestRef,
            "clientUid" to clientUid,
            "lat" to lat,
            "lng" to lng,
            "geohash" to geohash,
            "pickupAddress" to pickupAddress,
            "category" to category,
            "volumeTier" to volumeTier,
            "notes" to notes,
            "status" to "PENDING_DISPATCH",
            "createdAt" to createdAtValue,
            "updatedAt" to updatedAtValue
        )

        gatePhotoBase64?.let { map["gatePhotoBase64"] = it }
        gatePhotoUrl?.let { map["gatePhotoUrl"] = it }
        photos?.let { map["photos"] = it }
        residentPhoneNumber?.let { map["residentPhoneNumber"] = it }

        return map
    }
}
