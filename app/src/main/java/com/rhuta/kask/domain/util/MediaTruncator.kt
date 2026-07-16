package com.rhuta.kask.domain.util

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

/**
 * Robust audio truncation and container repair.
 * Supports MP4 containers (AAC/AMR) and Raw containers (MP3/WAV) via bitstream copying.
 */
object MediaTruncator {

    fun truncateAudio(context: Context, sourceUri: Uri, originalName: String, durationSeconds: Int = 600): File? {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(context, sourceUri, null)
            
            var audioTrackIndex = -1
            var originalFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                val mime = f.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrackIndex = i
                    originalFormat = f
                    break
                }
            }

            if ((audioTrackIndex == -1) || (originalFormat == null)) return null
            val mime = originalFormat.getString(MediaFormat.KEY_MIME) ?: ""

            // Decide strategy based on codec
            return if ((mime == "audio/mp4a-latm") || (mime == "audio/3gpp") || (mime == "audio/amr-wb")) {
                truncateMuxed(context, extractor, audioTrackIndex, originalFormat, originalName, durationSeconds)
            } else {
                // For MP3, WAV, etc., we use raw bitstream copy
                truncateRaw(context, extractor, audioTrackIndex, mime, originalName, durationSeconds)
            }
        } catch (e: Exception) {
            Log.e("Kask_Audio", "Truncation strategy failure: ${e.message}")
            return null
        } finally {
            extractor.release()
        }
    }

    private fun truncateMuxed(
        context: Context,
        extractor: MediaExtractor,
        trackIndex: Int,
        originalFormat: MediaFormat,
        originalName: String,
        durationSeconds: Int,
    ): File? {
        var muxer: MediaMuxer? = null
        var tempFile: File? = null
        try {
            extractor.selectTrack(trackIndex)
            val dir = File(context.cacheDir, "kask_uploads").apply { if (!exists()) mkdirs() }
            val cleanName = originalName.substringBeforeLast(".")
            tempFile = File(dir, "trunc_$cleanName.m4a")
            
            muxer = MediaMuxer(tempFile.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            
            val mime = originalFormat.getString(MediaFormat.KEY_MIME) ?: ""
            val format = MediaFormat.createAudioFormat(
                mime,
                originalFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE),
                originalFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT),
            )
            
            for (i in 0..2) {
                val csdKey = "csd-$i"
                if (originalFormat.containsKey(csdKey)) {
                    format.setByteBuffer(csdKey, originalFormat.getByteBuffer(csdKey))
                }
            }
            if (originalFormat.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) {
                format.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, originalFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
            }

            val muxerTrackIndex = muxer.addTrack(format)
            muxer.start()

            copySamples(extractor, muxer, muxerTrackIndex, durationSeconds)
            
            return tempFile
        } catch (e: Exception) {
            Log.e("Kask_Audio", "Muxed truncation crash: ${e.message}")
            tempFile?.delete()
            return null
        } finally {
            try { muxer?.stop() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
        }
    }

    private fun truncateRaw(
        context: Context,
        extractor: MediaExtractor,
        trackIndex: Int,
        mime: String,
        originalName: String,
        durationSeconds: Int,
    ): File? {
        var tempFile: File? = null
        try {
            extractor.selectTrack(trackIndex)
            val extension = when {
                mime.contains("mpeg") -> "mp3"
                mime.contains("wav") || mime.contains("x-wav") -> "wav"
                else -> originalName.substringAfterLast(".", "bin")
            }
            
            val dir = File(context.cacheDir, "kask_uploads").apply { if (!exists()) mkdirs() }
            val cleanName = originalName.substringBeforeLast(".")
            tempFile = File(dir, "trunc_$cleanName.$extension")
            
            FileOutputStream(tempFile).use { fos ->
                val buffer = ByteBuffer.allocate(512 * 1024)
                val limitUs = durationSeconds * 1_000_000L
                
                while (true) {
                    val sampleSize = extractor.readSampleData(buffer, 0)
                    if (sampleSize < 0) break
                    
                    if (extractor.sampleTime > limitUs) break
                    
                    val chunk = ByteArray(sampleSize)
                    buffer[chunk]
                    fos.write(chunk)
                    buffer.clear()
                    extractor.advance()
                }
            }
            return tempFile
        } catch (e: Exception) {
            Log.e("Kask_Audio", "Raw truncation crash: ${e.message}")
            tempFile?.delete()
            return null
        }
    }

    private fun copySamples(
        extractor: MediaExtractor,
        muxer: MediaMuxer,
        trackIndex: Int,
        durationSeconds: Int,
    ) {
        val buffer = ByteBuffer.allocate(512 * 1024)
        val bufferInfo = MediaCodec.BufferInfo()
        val limitUs = durationSeconds * 1_000_000L

        while (true) {
            bufferInfo.offset = 0
            bufferInfo.size = extractor.readSampleData(buffer, 0)
            if (bufferInfo.size < 0) break
            
            bufferInfo.presentationTimeUs = extractor.sampleTime
            if (bufferInfo.presentationTimeUs > limitUs) break

            var flags = 0
            if ((extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0) {
                flags = flags or MediaCodec.BUFFER_FLAG_KEY_FRAME
            }
            bufferInfo.flags = flags

            muxer.writeSampleData(trackIndex, buffer, bufferInfo)
            extractor.advance()
        }
    }
}
