package com.waste.link.data.models

import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date

data class User(
    @DocumentId
    val id: String = "",
    val email: String = "",
    val fullName: String = "",
    val phoneNumber: String = "",
    val preferredLanguage: String = "en",
    val isActive: Boolean = true, // Legacy, migrate to status later
    
    // Core Architecture Fields
    val role: String = "CLIENT", // CLIENT, WASTE_COMPANY, ADMIN
    val status: String = "UNVERIFIED", // UNVERIFIED, ACTIVE, RESTRICTED, SUSPENDED, BANNED
    val bannedUntil: Date? = null,
    val contractUntil: Date? = null,
    val deletedAt: Date? = null,

    @ServerTimestamp
    val createdAt: Date? = null,
    @ServerTimestamp
    val updatedAt: Date? = null
)
