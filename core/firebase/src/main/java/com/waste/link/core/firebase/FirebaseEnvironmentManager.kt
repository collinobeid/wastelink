package com.waste.link.core.firebase

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.waste.link.data.remote.FirebaseEmulatorConfig
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

/**
 * Manages Firebase environment configuration, emulator connections, and environment gates.
 */
object FirebaseEnvironmentManager {

    private const val TAG = "FirebaseEnvManager"

    private val currentEnvironment = AtomicReference(FirebaseEnvironment.LOCAL_EMULATOR)
    private val isProductionActive = AtomicBoolean(false)

    fun getEnvironment(): FirebaseEnvironment = currentEnvironment.get()

    fun setEnvironment(env: FirebaseEnvironment) {
        currentEnvironment.set(env)
    }

    fun isProductionActivated(): Boolean = isProductionActive.get()

    fun setProductionActivated(activated: Boolean) {
        isProductionActive.set(activated)
    }

    /**
     * Configures Firebase services according to the active environment.
     *
     * - LOCAL_EMULATOR: Connects to local emulators at [emulatorHost] (Auth 9099, Firestore 8080).
     * - REMOTE_STAGING: Connects to remote staging Firebase project without local emulators.
     * - PRODUCTION: Fails closed unless explicitly authorized via [setProductionActivated].
     */
    fun configureFirebase(
        firebaseAuth: FirebaseAuth,
        firestore: FirebaseFirestore,
        env: FirebaseEnvironment = getEnvironment(),
        emulatorHost: String = FirebaseEmulatorConfig.DEFAULT_ANDROID_EMULATOR_HOST
    ) {
        currentEnvironment.set(env)
        when (env) {
            FirebaseEnvironment.LOCAL_EMULATOR -> {
                Log.i(TAG, "Configuring Firebase for LOCAL_EMULATOR at $emulatorHost")
                FirebaseEmulatorConfig.connectToEmulators(
                    firebaseAuth = firebaseAuth,
                    firestore = firestore,
                    host = emulatorHost
                )
            }
            FirebaseEnvironment.REMOTE_STAGING -> {
                Log.i(TAG, "Configuring Firebase for REMOTE_STAGING (remote endpoints active)")
            }
            FirebaseEnvironment.PRODUCTION -> {
                check(isProductionActivated()) {
                    "Production Firebase environment is not activated yet. WasteLink is operating in staging mode."
                }
                Log.i(TAG, "Configuring Firebase for PRODUCTION")
            }
        }
    }
}
