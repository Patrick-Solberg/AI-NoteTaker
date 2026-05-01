package com.example.ai_notetaker.data.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream

object ImageHelper {
    /**
     * Get the directory where images are stored
     */
    fun getImageStorageDirectory(context: Context): File {
        val imagesDir = File(context.filesDir, "images")
        if (!imagesDir.exists()) {
            imagesDir.mkdirs()
        }
        return imagesDir
    }
    
    /**
     * Save a bitmap to internal storage and return the file path
     */
    fun saveImageFile(context: Context, bitmap: Bitmap, noteId: Long): String {
        val imagesDir = getImageStorageDirectory(context)
        val fileName = "note_${noteId}_${System.currentTimeMillis()}.jpg"
        val imageFile = File(imagesDir, fileName)
        
        FileOutputStream(imageFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
        }
        
        return imageFile.absolutePath
    }
    
    /**
     * Load a bitmap from a file path
     */
    fun loadBitmap(filePath: String): Bitmap? {
        return try {
            BitmapFactory.decodeFile(filePath)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Delete an image file
     */
    fun deleteImageFile(filePath: String): Boolean {
        val imageFile = File(filePath)
        return if (imageFile.exists()) {
            imageFile.delete()
        } else {
            false
        }
    }
}
