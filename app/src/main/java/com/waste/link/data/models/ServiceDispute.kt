package com.waste.link.data.models

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class ServiceDispute(
    @DocumentId
    val id: String = "",
    val reporterId: String = "",
    val disputedEntityType: String = "", // 'CLIENT', 'OPERATIONAL_UNIT', 'PICKUP_REQUEST'
    val disputedEntityId: String = "",
    val snapshotData: String = "{}", // Stored as JSON string
    val reason: String = "",
    val status: String = "PENDING_REVIEW",
    @ServerTimestamp
    val createdAt: Date? = null,
    @ServerTimestamp
    val updatedAt: Date? = null
)
