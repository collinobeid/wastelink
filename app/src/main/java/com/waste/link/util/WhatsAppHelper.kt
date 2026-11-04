package com.waste.link.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object WhatsAppHelper {

    fun openWhatsAppChat(context: Context, phoneNumber: String, message: String) {
        val isAppInstalled = isPackageInstalled(context, "com.whatsapp") || 
                             isPackageInstalled(context, "com.whatsapp.w4b")
                             
        if (!isAppInstalled) {
            Toast.makeText(context, "WhatsApp not installed. Redirecting to Play Store.", Toast.LENGTH_SHORT).show()
            try {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.whatsapp")))
            } catch (e: Exception) {
                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.whatsapp")))
            }
            return
        }

        try {
            val cleanPhone = phoneNumber.replace(Regex("[^0-9]"), "")
            val encodedMessage = java.net.URLEncoder.encode(message, "UTF-8")
            val url = "intent://send?phone=$cleanPhone&text=$encodedMessage#Intent;scheme=whatsapp;package=com.whatsapp;end"
            
            val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Error opening WhatsApp", Toast.LENGTH_SHORT).show()
        }
    }

    private fun isPackageInstalled(context: Context, packageName: String): Boolean {
        return try {
            context.packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: android.content.pm.PackageManager.NameNotFoundException) {
            false
        }
    }
}
