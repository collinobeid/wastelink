package com.waste.link

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import com.waste.link.ui.WasteLinkNavGraph
import com.waste.link.ui.theme.WasteLinkTheme
import dagger.hilt.android.AndroidEntryPoint
import android.Manifest
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import com.google.firebase.messaging.FirebaseMessaging
import android.util.Log

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val requestPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                // Permission is granted. Wait for Firebase to provide the token
                FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                    if (!task.isSuccessful) return@addOnCompleteListener
                    val token = task.result
                    Log.d("FCM", "Token: $token")
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (!task.isSuccessful) return@addOnCompleteListener
                Log.d("FCM", "Token: ${task.result}")
            }
        }
        
        enableEdgeToEdge()
        setContent {
            WasteLinkTheme {
                WasteLinkNavGraph(
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}