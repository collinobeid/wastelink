package com.waste.link.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import java.net.URLEncoder

object WhatsAppUtils {
    
    fun openWhatsAppChat(context: Context, phoneNumber: String, message: String) {
        try {
            // Clean the phone number to be purely numeric, with an optional leading +
            val cleanedNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
            
            // Format the number so it definitely works with wa.me (e.g. without + is usually safer, but both work if properly formatted)
            val finalNumber = if (cleanedNumber.startsWith("+")) cleanedNumber.substring(1) else cleanedNumber
            
            val url = "https://api.whatsapp.com/send?phone=$finalNumber&text=${URLEncoder.encode(message, "UTF-8")}"
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            // This flag helps if it's launched from a non-activity context, though here we pass the composable context
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "WhatsApp is not installed or unable to open.", Toast.LENGTH_SHORT).show()
        }
    }
}
