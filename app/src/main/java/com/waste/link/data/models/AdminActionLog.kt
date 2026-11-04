package com.waste.link.data.models

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class AdminActionLog(
    @DocumentId
    val id: String = "",
    val adminId: String = "",
    val targetUserId: String = "",
    val action: String = "", // SUSPENDED, BANNED, GROUNDED_TRUCK
    val reason: String = "",
    @ServerTimestamp
    val createdAt: Date? = null
)
