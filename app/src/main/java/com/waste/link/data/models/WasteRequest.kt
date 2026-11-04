package com.waste.link.data.models

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class WasteRequest(
    @DocumentId
    val id: String = "",
    val requestRef: String = "",
    val clientUid: String = "",
    val geohash: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val pickupAddress: String = "",
    val category: String = "",
    val volumeTier: String = "",
    val notes: String = "",
    
    // Base64 Images (Storage Alternative)
    val gatePhotoBase64: String? = null,
    val pocPhotoBase64: String? = null,
    
    // Firebase Storage URLs
    val gatePhotoUrl: String? = null,
    val pocPhotoUrl: String? = null,
    val photos: List<String> = emptyList(), // Support for multi-photo gallery
    
    // Unidirectional State Machine
    // DRAFT -> PENDING_DISPATCH -> ASSIGNED_TO_FLEET -> EN_ROUTE -> 
    // ARRIVED_AT_LOCATION -> COLLECTION_VERIFIED -> IN_TRANSIT_TO_DUMP -> COMPLETED
    val status: String = "PENDING_DISPATCH",
    
    val assignedTruckId: String? = null,
    val assignedAt: Date? = null,
    val enRouteAt: Date? = null,
    val arrivedAt: Date? = null,
    val collectedAt: Date? = null,
    val completedAt: Date? = null,
    val failedAt: Date? = null,
    val failureReason: String? = null,
    
    // Pricing and Negotiation
    val agreedPrice: Double? = null,
    val residentPhoneNumber: String? = null,
    val collectorPhoneNumber: String? = null,
    
    @ServerTimestamp
    val createdAt: Date? = null,
    @ServerTimestamp
    val updatedAt: Date? = null
)
