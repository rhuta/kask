package com.rhuta.kask.data.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileInputStream
import java.io.RandomAccessFile
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadManager @Inject constructor(
    private val client: OkHttpClient,
) {
    /**
     * Downloads a file with support for resuming (HTTP Range) and integrity verification.
     */
    fun downloadFile(
        url: String, 
        destination: File, 
        expectedSize: Long? = null, 
        expectedHash: String? = null
    ): Flow<DownloadStatus> = flow {
        var raf: RandomAccessFile? = null
        try {
            emit(DownloadStatus.Started)
            
            // 1. Detect existing progress
            var existingSize = if (destination.exists()) destination.length() else 0L
            
            // RELAXED SECURITY: Only reset if existing file is clearly wrong (e.g., > 110% of expected)
            if (expectedSize != null && existingSize > (expectedSize * 1.1)) {
                destination.delete()
                existingSize = 0L
            }

            Log.d("Kask_Download", "Starting download: $url, Existing size: $existingSize")

            // 2. Build Request with Range header if needed
            val requestBuilder = Request.Builder().url(url)
            if (existingSize > 0) {
                requestBuilder.addHeader("Range", "bytes=$existingSize-")
            }
            
            val response = client.newCall(requestBuilder.build()).execute()
            
            // Handle server refusing Range request (starts from 0)
            val isResuming = (response.code == 206)
            if (!response.isSuccessful && !isResuming) {
                emit(DownloadStatus.Error("Server error: ${response.code}"))
                return@flow
            }

            val body = response.body ?: throw Exception("Empty response body")
            val contentLen = body.contentLength()
            val totalBytes = if (isResuming) (existingSize + contentLen) else contentLen
            
            // 3. Verify enough space on device
            // (Handled at higher level, but could double check here)
            
            val inputStream = body.byteStream()
            raf = RandomAccessFile(destination, "rw")
            
            if (isResuming) {
                raf.seek(existingSize)
                Log.d("Kask_Download", "Resuming from $existingSize. Target: $totalBytes")
            } else {
                raf.setLength(0)
                existingSize = 0L
                Log.d("Kask_Download", "Starting fresh. Target: $totalBytes")
            }

            // 4. Stream data with robust error handling
            val buffer = ByteArray(64 * 1024) 
            var bytesRead: Int
            var totalBytesRead = existingSize
            var lastUpdate = 0L

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                raf.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead
                
                // Throttle progress emissions to save UI thread
                val now = System.currentTimeMillis()
                if (now - lastUpdate > 200) {
                    val progress = if (totalBytes > 0) (totalBytesRead.toFloat() / totalBytes) else -1f
                    emit(DownloadStatus.Progress(progress))
                    lastUpdate = now
                }
            }

            // 5. POST-DOWNLOAD INTEGRITY CHECK
            emit(DownloadStatus.Progress(0.99f)) // "Finalizing..."
            
            // LAYER A: Mandatory Hash Check (Absolute Authority)
            if (expectedHash != null) {
                val actualHash = calculateFileHash(destination)
                if (actualHash == expectedHash) {
                    emit(DownloadStatus.Finished(destination))
                    Log.d("Kask_Download", "Hash Verified & Finished: ${destination.name}")
                    return@flow
                } else {
                    // Hash mismatch is definitive proof of corruption
                    destination.delete()
                    emit(DownloadStatus.Error("Integrity check failed: Data is corrupted (Hash mismatch)."))
                    return@flow
                }
            }

            // LAYER B: Fuzzy Size Check (Fallback if no hash provided)
            // Passes if within 90% to 110% of expected range
            if (expectedSize != null) {
                val actualSize = destination.length()
                val minAllowed = (expectedSize * 0.9).toLong()
                val maxAllowed = (expectedSize * 1.1).toLong()
                
                if (actualSize !in minAllowed..maxAllowed) {
                    destination.delete()
                    emit(DownloadStatus.Error("File size is outside of valid range (90%-110%)."))
                    return@flow
                }
            }

            emit(DownloadStatus.Finished(destination))
            Log.d("Kask_Download", "Size within tolerance & Finished: ${destination.name}")
            
        } catch (e: java.net.SocketException) {
            emit(DownloadStatus.Error("Network disconnected. Progress saved."))
        } catch (e: Exception) {
            Log.e("Kask_Download", "Download failure", e)
            emit(DownloadStatus.Error(e.message ?: "Unknown error"))
        } finally {
            try { raf?.close() } catch (_: Exception) {}
        }
    }.flowOn(Dispatchers.IO)

    private fun calculateFileHash(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(1024 * 1024) // 1MB buffer for hashing
        FileInputStream(file).use { input ->
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

sealed class DownloadStatus {
    object Started : DownloadStatus()
    data class Progress(val progress: Float) : DownloadStatus()
    data class Finished(val file: File) : DownloadStatus()
    data class Error(val message: String) : DownloadStatus()
}
