package com.waste.link.core.firebase

/**
 * Operating environments for Firebase services in WasteLink.
 */
enum class FirebaseEnvironment(val id: String, val displayName: String) {
    LOCAL_EMULATOR("emulator", "Local Firebase Emulator (Auth 9099, Firestore 8080)"),
    REMOTE_STAGING("staging", "Remote Staging Firebase"),
    PRODUCTION("production", "Production Firebase (Unactivated)")
}
