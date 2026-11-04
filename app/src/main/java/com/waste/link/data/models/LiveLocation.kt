package com.waste.link.data.models

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class LiveLocation(
    @DocumentId
    val id: String = "",
    val collectorId: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val heading: Float = 0f,
    @ServerTimestamp
    val timestamp: Date? = null
)
