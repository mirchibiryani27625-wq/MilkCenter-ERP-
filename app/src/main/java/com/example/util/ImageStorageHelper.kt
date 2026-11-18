package com.example.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

object ImageStorageHelper {

    /**
     * Saves a captured Bitmap into compressed JPEG file in internal storage
     */
    fun saveCompressedBitmap(context: Context, bitmap: Bitmap, prefix: String = "farmer"): String? {
        return try {
            val dir = File(context.filesDir, "farmer_photos")
            if (!dir.exists()) dir.mkdirs()

            val file = File(dir, "${prefix}_${System.currentTimeMillis()}.jpg")
            val outputStream = FileOutputStream(file)
            // Compress with 85% quality to save storage while keeping high clarity
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
            outputStream.flush()
            outputStream.close()

            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Copies and compresses a Uri from gallery into internal storage
     */
    fun saveCompressedUri(context: Context, sourceUri: Uri, prefix: String = "farmer"): String? {
        return try {
            val inputStream: InputStream? = context.contentResolver.openInputStream(sourceUri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (bitmap != null) {
                saveCompressedBitmap(context, bitmap, prefix)
            } else {
                sourceUri.toString()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            sourceUri.toString()
        }
    }

    /**
     * Permanently saves a scanned machine display / receipt Bitmap for Admin Verification & Audit.
     */
    fun saveScanBitmap(context: Context, bitmap: Bitmap, prefix: String = "scan"): String? {
        return try {
            val dir = File(context.filesDir, "scan_images")
            if (!dir.exists()) dir.mkdirs()

            val file = File(dir, "${prefix}_${System.currentTimeMillis()}.jpg")
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
                out.flush()
            }
            file.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Permanently copies a scanned receipt/display Uri into app internal storage.
     */
    fun saveScanUri(context: Context, sourceUri: Uri, prefix: String = "scan"): String? {
        return try {
            if (sourceUri.scheme == "file" && sourceUri.path != null) {
                val f = File(sourceUri.path!!)
                if (f.exists() && f.absolutePath.contains("scan_images")) {
                    return f.absolutePath
                }
            }
            val inputStream: InputStream? = context.contentResolver.openInputStream(sourceUri)
            val bitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()
            if (bitmap != null) {
                saveScanBitmap(context, bitmap, prefix)
            } else {
                sourceUri.toString()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            sourceUri.toString()
        }
    }
}
