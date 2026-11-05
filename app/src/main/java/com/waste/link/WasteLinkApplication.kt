package com.waste.link

import android.app.Application
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.waste.link.core.firebase.FirebaseEnvironment
import com.waste.link.core.firebase.FirebaseEnvironmentManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class WasteLinkApplication : Application() {

    @Inject
    lateinit var firebaseAuth: FirebaseAuth

    @Inject
    lateinit var firestore: FirebaseFirestore

    override fun onCreate() {
        super.onCreate()
        val envName = BuildConfig.FIREBASE_ENV
        val expectedProjectId = BuildConfig.FIREBASE_PROJECT_ID
        val actualProjectId = try {
            FirebaseApp.getInstance().options.projectId
        } catch (_: Exception) {
            null
        }

        Log.i(
            "WasteLinkApp",
            "Initializing WasteLink: env=$envName, expectedProjectId=$expectedProjectId, actualProjectId=$actualProjectId"
        )

        FirebaseEnvironmentManager.validateProjectConfiguration(
            expectedProjectId = expectedProjectId,
            actualProjectId = actualProjectId
        )

        val env = when (envName.uppercase()) {
            "EMULATOR" -> FirebaseEnvironment.LOCAL_EMULATOR
            "STAGING" -> FirebaseEnvironment.REMOTE_STAGING
            "PRODUCTION" -> FirebaseEnvironment.PRODUCTION
            else -> FirebaseEnvironment.LOCAL_EMULATOR
        }
        FirebaseEnvironmentManager.setEnvironment(env)

        FirebaseEnvironmentManager.configureFirebase(
            firebaseAuth = firebaseAuth,
            firestore = firestore,
            env = env
        )
    }
}
