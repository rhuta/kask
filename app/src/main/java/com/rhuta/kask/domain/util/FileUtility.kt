package com.rhuta.kask.domain.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.io.File

/**
 * Utility for high-performance, low-RAM file operations.
 */
object FileUtility {

    /**
     * Streams data from a Uri to a local file using a small 8KB buffer.
     * Prevents OOM crashes when importing large audio or document files.
     */
    fun copyUriToLocalStorage(context: Context, sourceUri: Uri, destinationFile: File): Boolean {
        return try {
            context.contentResolver.openInputStream(sourceUri)?.use { inputStream ->
                destinationFile.outputStream().use { outputStream ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                    }
                }
            }
            true
        } catch (e: Exception) {
            Log.e("Kask_File", "Failed to stream file to storage: ${e.message}")
            false
        }
    }

    /**
     * Resolves the actual display name from a Content URI.
     */
    fun getFileName(context: Context, uri: Uri): String? {
        if (uri.scheme == "file") return uri.lastPathSegment
        
        var name: String? = null
        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if ((nameIndex != -1) && cursor.moveToFirst()) {
                    name = cursor.getString(nameIndex)
                }
            }
        } catch (e: Exception) {
            Log.e("Kask_File", "Failed to resolve file name: ${e.message}")
        }
        return name
    }

    /**
     * Generates a unique cache file for an incoming upload.
     * Uses AINI's consistent naming pattern.
     */
    fun createCacheFile(context: Context, extension: String = "tmp"): File {
        val dir = File(context.cacheDir, "kask_uploads").apply { if (!exists()) mkdirs() }
        val file = File(dir, "up_${System.currentTimeMillis()}.$extension")
        Log.d("Kask_File", "Cache file created: ${file.absolutePath}")
        return file
    }

    /**
     * Creates a cache file with a specific name, ensuring it exists in the kask_uploads dir.
     */
    fun createCacheFileWithName(context: Context, name: String): File {
        val dir = File(context.cacheDir, "kask_uploads").apply { if (!exists()) mkdirs() }
        val file = File(dir, name)
        Log.d("Kask_File", "Named cache file created: ${file.absolutePath}")
        return file
    }

    /**
     * Gets audio duration in milliseconds.
     */
    fun getAudioDurationMs(context: Context, uri: Uri): Long {
        return try {
            val retriever = android.media.MediaMetadataRetriever()
            retriever.setDataSource(context, uri)
            val duration = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
            retriever.release()
            duration?.toLong() ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    /**
     * Clears all temporary upload files.
     */
    fun clearUploadCache(context: Context) {
        val dir = File(context.cacheDir, "kask_uploads")
        if (dir.exists()) {
            dir.listFiles()?.forEach { it.delete() }
        }
    }
}
