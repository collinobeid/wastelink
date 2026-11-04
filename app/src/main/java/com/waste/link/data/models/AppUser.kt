package com.waste.link.data.models

data class AppUser(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val role: String = "resident", // "resident", "collector", "admin"
    val isVerified: Boolean = false,
    
    // Core Architecture Fields
    val status: String = "UNVERIFIED", // UNVERIFIED, ACTIVE, RESTRICTED, SUSPENDED, BANNED
    val bannedUntil: Long? = null, // Using Long for timestamp for Firebase compatibility
    val contractUntil: Long? = null,
    val deletedAt: Long? = null
)
