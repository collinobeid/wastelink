package com.waste.link.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.waste.link.MainActivity
import com.waste.link.R

class WasteLinkMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // Handle FCM messages here.
        remoteMessage.notification?.let {
            sendNotification(it.title ?: "WasteLink", it.body ?: "")
        }
    }

    override fun onNewToken(token: String) {
        // Send token to your server if needed.
        // For MVP, we can just log it or save it to Firestore.
        saveTokenToFirestore(token)
    }

    private fun saveTokenToFirestore(token: String) {
        // Implementation to save token under the user's document
        val db = com.google.firebase.firestore.FirebaseFirestore.getInstance()
        val auth = com.google.firebase.auth.FirebaseAuth.getInstance()
        auth.currentUser?.uid?.let { uid ->
            db.collection("users").document(uid)
                .update("fcmToken", token)
        }
    }

    private fun sendNotification(title: String, messageBody: String) {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_ONE_SHOT
        )

        val channelId = "WasteLink_Notifications"
        val defaultSoundUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        val notificationBuilder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(messageBody)
            .setAutoCancel(true)
            .setSound(defaultSoundUri)
            .setContentIntent(pendingIntent)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Since android Oreo notification channel is needed.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "WasteLink Status Updates",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            notificationManager.createNotificationChannel(channel)
        }

        notificationManager.notify(0, notificationBuilder.build())
    }
}
