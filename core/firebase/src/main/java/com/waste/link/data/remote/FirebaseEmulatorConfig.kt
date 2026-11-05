package com.waste.link.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.MemoryCacheSettings
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Explicit Firebase Emulator configuration helper for local Android instrumentation testing.
 *
 * Security & Production Guarantee:
 * - Never invoked automatically in production runtime paths (`AppModule` does not call this).
 * - Only connects to the local Firebase Auth (`9099`) and Firestore (`8080`) emulators when
 *   explicitly requested by an emulator test harness.
 */
object FirebaseEmulatorConfig {

    const val DEFAULT_ANDROID_EMULATOR_HOST = "10.0.2.2"
    const val DEFAULT_LOCALHOST = "127.0.0.1"
    const val DEFAULT_AUTH_PORT = 9099
    const val DEFAULT_FIRESTORE_PORT = 8080

    private val isConnected = AtomicBoolean(false)
    private val connectedHost = AtomicReference<String?>(null)

    /**
     * Returns `true` only after [connectToEmulators] has been explicitly invoked.
     */
    fun isEmulatorConnected(): Boolean = isConnected.get()

    /**
     * Returns the emulator host (`"10.0.2.2"` or `"127.0.0.1"`) when connected, or `null` in production.
     */
    fun currentHost(): String? = connectedHost.get()

    /**
     * Resets emulator connection state for isolated unit testing.
     */
    fun resetForTesting() {
        isConnected.set(false)
        connectedHost.set(null)
    }

    /**
     * Connects [firebaseAuth] and [firestore] to the local Firebase Emulator Suite if not already connected.
     */
    @Synchronized
    fun connectToEmulators(
        firebaseAuth: FirebaseAuth,
        firestore: FirebaseFirestore,
        host: String = DEFAULT_ANDROID_EMULATOR_HOST,
        authPort: Int = DEFAULT_AUTH_PORT,
        firestorePort: Int = DEFAULT_FIRESTORE_PORT
    ) {
        if (isConnected.compareAndSet(false, true)) {
            connectedHost.set(host)
            firebaseAuth.useEmulator(host, authPort)
            firestore.useEmulator(host, firestorePort)
            val settings = FirebaseFirestoreSettings.Builder()
                .setLocalCacheSettings(MemoryCacheSettings.newBuilder().build())
                .build()
            firestore.firestoreSettings = settings
        }
    }
}
