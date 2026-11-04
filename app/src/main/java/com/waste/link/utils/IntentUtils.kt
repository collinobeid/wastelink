package com.waste.link.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object IntentUtils {
    
    fun copyToClipboard(context: Context, label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    fun openWhatsApp(context: Context, phoneNumber: String, message: String = "") {
        // Remove any non-digit characters except '+'
        val cleanNumber = phoneNumber.replace(Regex("[^0-9+]"), "")
        
        // WhatsApp API format
        val url = "https://wa.me/$cleanNumber?text=${Uri.encode(message)}"
        
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse(url)
        }
        
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "WhatsApp is not installed", Toast.LENGTH_SHORT).show()
        }
    }
}
