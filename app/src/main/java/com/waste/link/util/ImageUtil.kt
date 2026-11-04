package com.waste.link.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import java.io.ByteArrayOutputStream

object ImageUtil {
    fun encodeBitmapToBase64(bitmap: Bitmap, quality: Int = 70): String {
        val byteArray = compressBitmapToByteArray(bitmap, quality)
        return Base64.encodeToString(byteArray, Base64.DEFAULT)
    }

    fun compressBitmapToByteArray(bitmap: Bitmap, quality: Int = 80, maxEdge: Int = 1600): ByteArray {
        var scaledBitmap = bitmap
        val width = bitmap.width
        val height = bitmap.height
        
        if (width > maxEdge || height > maxEdge) {
            val ratio = Math.min(maxEdge.toFloat() / width, maxEdge.toFloat() / height)
            val newWidth = Math.round(ratio * width)
            val newHeight = Math.round(ratio * height)
            scaledBitmap = Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
        }
        
        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        return outputStream.toByteArray()
    }

    fun decodeBase64ToBitmap(base64Str: String): Bitmap? {
        return try {
            val decodedBytes = Base64.decode(base64Str, Base64.DEFAULT)
            BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        } catch (e: Exception) {
            null
        }
    }
}
