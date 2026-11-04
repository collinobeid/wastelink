package com.waste.link.data.models

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class OperationalUnit(
    @DocumentId
    val id: String = "",
    val companyId: String = "",
    val name: String = "", // e.g., "Truck Alpha - Route 4"
    val licensePlate: String = "",
    val status: String = "DRAFT", // DRAFT, PENDING_INSPECTION, ACTIVE, GROUNDED
    val totalPickups: Int = 0,
    val rating: Double = 0.0,
    @ServerTimestamp
    val createdAt: Date? = null,
    @ServerTimestamp
    val updatedAt: Date? = null
)
