package com.rhuta.kask.domain.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.io.File
import java.io.RandomAccessFile
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.concurrent.thread

/**
 * Standard 16kHz Mono WAV Recorder.
 * Implements manual 44-byte WAV header writing for bit-perfect engine compatibility.
 */
@Singleton
class AudioRecorder @Inject constructor(
    private val context: Context
) {
    private var audioRecord: AudioRecord? = null
    private var isRecording = false

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)

    @SuppressLint("MissingPermission")
    fun start(outputFile: File) {
        if (isRecording) return

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e("Kask_Audio", "AudioRecord failed to initialize")
                return
            }

            audioRecord?.startRecording()
            isRecording = true

            thread {
                val audioData = ByteArray(bufferSize)
                try {
                    outputFile.outputStream().use { outputStream ->
                        // 1. Write placeholder for WAV header (44 bytes)
                        val header = ByteArray(44)
                        outputStream.write(header)

                        var totalAudioLen = 0L
                        while (isRecording) {
                            val readBytes = audioRecord?.read(audioData, 0, bufferSize) ?: 0
                            if (readBytes > 0) {
                                outputStream.write(audioData, 0, readBytes)
                                totalAudioLen += readBytes
                            }
                        }
                        
                        // 2. Rewrite header with finalized sizes
                        val finalHeader = createWavHeader(totalAudioLen)
                        RandomAccessFile(outputFile.absolutePath, "rw").use { raf ->
                            raf.seek(0)
                            raf.write(finalHeader)
                        }
                        Log.d("Kask_Audio", "Recording saved: ${outputFile.absolutePath}, size: $totalAudioLen")
                    }
                } catch (e: Exception) {
                    Log.e("Kask_Audio", "Recording stream error: ${e.message}")
                }
            }
        } catch (e: Exception) {
            Log.e("Kask_Audio", "Failed to start recording: ${e.message}")
        }
    }

    private fun createWavHeader(audioLen: Long): ByteArray {
        val totalLen = audioLen + 36
        val channels = 1
        val byteRate = 16000L * channels * 16 / 8
        val header = ByteArray(44)
        
        header[0] = 'R'.code.toByte()
        header[1] = 'I'.code.toByte()
        header[2] = 'F'.code.toByte()
        header[3] = 'F'.code.toByte()
        header[4] = (totalLen and 0xff).toByte()
        header[5] = (totalLen shr 8 and 0xff).toByte()
        header[6] = (totalLen shr 16 and 0xff).toByte()
        header[7] = (totalLen shr 24 and 0xff).toByte()
        
        header[8] = 'W'.code.toByte()
        header[9] = 'A'.code.toByte()
        header[10] = 'V'.code.toByte()
        header[11] = 'E'.code.toByte()
        
        header[12] = 'f'.code.toByte()
        header[13] = 'm'.code.toByte()
        header[14] = 't'.code.toByte()
        header[15] = ' '.code.toByte()
        
        header[16] = 16 // Subchunk1Size (16 for PCM)
        header[17] = 0
        header[18] = 0
        header[19] = 0
        
        header[20] = 1 // AudioFormat (1 for PCM)
        header[21] = 0
        
        header[22] = channels.toByte()
        header[23] = 0
        
        header[24] = (sampleRate.toLong() and 0xff).toByte()
        header[25] = (sampleRate.toLong() shr 8 and 0xff).toByte()
        header[26] = (sampleRate.toLong() shr 16 and 0xff).toByte()
        header[27] = (sampleRate.toLong() shr 24 and 0xff).toByte()
        
        header[28] = (byteRate and 0xff).toByte()
        header[29] = (byteRate shr 8 and 0xff).toByte()
        header[30] = (byteRate shr 16 and 0xff).toByte()
        header[31] = (byteRate shr 24 and 0xff).toByte()
        
        header[32] = (channels * 16 / 8).toByte() // BlockAlign
        header[33] = 0
        header[34] = 16 // BitsPerSample
        header[35] = 0
        
        header[36] = 'd'.code.toByte()
        header[37] = 'a'.code.toByte()
        header[38] = 't'.code.toByte()
        header[39] = 'a'.code.toByte()
        
        header[40] = (audioLen and 0xff).toByte()
        header[41] = (audioLen shr 8 and 0xff).toByte()
        header[42] = (audioLen shr 16 and 0xff).toByte()
        header[43] = (audioLen shr 24 and 0xff).toByte()
        
        return header
    }

    fun stop() {
        if (!isRecording) return
        isRecording = false
        
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e("Kask_Audio", "Error stopping recorder: ${e.message}")
        }
        audioRecord = null
    }
}
