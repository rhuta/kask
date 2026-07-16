package com.rhuta.kask.data.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.RandomAccessFile
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadManager @Inject constructor(
    private val client: OkHttpClient,
) {
    /**
     * Downloads a file with support for resuming (HTTP Range).
     */
    fun downloadFile(url: String, destination: File): Flow<DownloadStatus> = flow {
        var raf: RandomAccessFile? = null
        try {
            emit(DownloadStatus.Started)
            
            // 1. Detect existing progress
            val existingSize = if (destination.exists()) destination.length() else 0L
            Log.d("Kask_Download", "Starting download: $url, Existing size: $existingSize")

            // 2. Build Request with Range header if needed
            val requestBuilder = Request.Builder().url(url)
            if (existingSize > 0) {
                requestBuilder.addHeader("Range", "bytes=$existingSize-")
            }
            
            val response = client.newCall(requestBuilder.build()).execute()
            if ((!response.isSuccessful) && (response.code != 206)) {
                emit(DownloadStatus.Error("Download failed: ${response.code}"))
                return@flow
            }

            val body = response.body ?: throw Exception("Empty response body")
            val contentLen = body.contentLength()
            
            // 3. Handle Resuming vs. Starting over
            // 206 = Partial Content (Resuming supported), 200 = OK (Starting over)
            val isResuming = (response.code == 206)
            val totalBytes = if (isResuming) (existingSize + contentLen) else contentLen
            val inputStream = body.byteStream()
            
            raf = RandomAccessFile(destination, "rw")
            if (isResuming) {
                raf.seek(existingSize)
                Log.d("Kask_Download", "Resuming download from $existingSize bytes. Total: $totalBytes")
            } else {
                raf.setLength(0) // Start fresh
                Log.d("Kask_Download", "Server doesn't support resuming or fresh start. Total: $totalBytes")
            }

            // 4. Stream data and emit progress
            val buffer = ByteArray(64 * 1024) // 64KB buffer for faster large file writes
            var bytesRead: Int
            var totalBytesRead = if (isResuming) existingSize else 0L

            while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                raf.write(buffer, 0, bytesRead)
                totalBytesRead += bytesRead
                
                val progress = if (totalBytes > 0) (totalBytesRead.toFloat() / totalBytes) else -1f
                emit(DownloadStatus.Progress(progress))
            }

            emit(DownloadStatus.Finished(destination))
            Log.d("Kask_Download", "Download complete: ${destination.absolutePath}")
            
        } catch (e: Exception) {
            Log.e("Kask_Download", "Download crash: ${e.message}", e)
            emit(DownloadStatus.Error(e.message ?: "Unknown download error"))
        } finally {
            try { raf?.close() } catch (_: Exception) {}
        }
    }.flowOn(Dispatchers.IO)
}

sealed class DownloadStatus {
    object Started : DownloadStatus()
    data class Progress(val progress: Float) : DownloadStatus()
    data class Finished(val file: File) : DownloadStatus()
    data class Error(val message: String) : DownloadStatus()
}
