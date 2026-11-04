package com.waste.link.data.models

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class Dispute(
    @DocumentId
    val id: String = "",
    val requestId: String = "",
    val residentId: String = "",
    val collectorId: String = "",
    val status: String = "OPEN", // OPEN, INVESTIGATING, RESOLVED
    val reason: String = "",
    val resolutionOutcome: String? = null, // Refund, Warning, Dismissed
    
    val dueBy: Date? = null,
    
    @ServerTimestamp
    val createdAt: Date? = null,
    @ServerTimestamp
    val updatedAt: Date? = null
)
