package com.waste.link

import android.app.Application
import android.util.Log
import com.waste.link.core.firebase.FirebaseEnvironment
import com.waste.link.core.firebase.FirebaseEnvironmentManager
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class WasteLinkApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        val envName = BuildConfig.FIREBASE_ENV
        Log.i("WasteLinkApp", "Initializing WasteLink Application with Firebase Environment: $envName")
        when (envName.uppercase()) {
            "EMULATOR" -> FirebaseEnvironmentManager.setEnvironment(FirebaseEnvironment.LOCAL_EMULATOR)
            "STAGING" -> FirebaseEnvironmentManager.setEnvironment(FirebaseEnvironment.REMOTE_STAGING)
            "PRODUCTION" -> FirebaseEnvironmentManager.setEnvironment(FirebaseEnvironment.PRODUCTION)
            else -> FirebaseEnvironmentManager.setEnvironment(FirebaseEnvironment.LOCAL_EMULATOR)
        }
    }
}
