package com.rhuta.kask.domain.engine

import android.util.Log

/**
 * JNI Bridge for llama.cpp b9789
 * Modern streaming implementation with MTMD and Native Template support.
 */
class LlamaBridge {

    companion object {
        private var isLoaded = false

        init {
            val libs = listOf(
                "c++_shared",
                "omp",
                "ggml-base",
                "ggml-cpu",
                "ggml",
                "llama",
                "mtmd",
                "kask_native"
            )

            var allSuccess = true
            for (lib in libs) {
                try {
                    System.loadLibrary(lib)
                    Log.d("LlamaBridge", "JNI: $lib LOADED")
                } catch (e: Throwable) {
                    Log.e("LlamaBridge", "JNI: $lib FAILED: ${e.message}")
                    allSuccess = false
                }
            }
            isLoaded = allSuccess
        }

        fun isNativeLoaded(): Boolean = isLoaded
    }

    external fun loadModel(modelPath: String): Long
    @JvmOverloads
    external fun createContext(modelPtr: Long, nCtx: Int = 4096, nBatch: Int = 512, useMtp: Boolean = false): Long
    external fun getMtpHeads(modelPtr: Long): Int
    
    /**
     * SYSTEMATIC: Applies the GGUF's internal chat template to a list of messages.
     */
    external fun applyChatTemplate(modelPtr: Long, roles: Array<String>, contents: Array<String>, addAssistantSlot: Boolean): String
    
    external fun getMarker(): String
    
    /**
     * Stable Streaming Completion with Temperature Control
     */
    external fun completionStream(ctxPtr: Long, prompt: String, temperature: Float, callback: TokenCallback)

    /**
     * MTMD Multimodal Streaming Completion with Temperature Control
     */
    external fun multimodalCompletionStream(ctxPtr: Long, mtmdPtr: Long, prompt: String, temperature: Float, bitmap: android.graphics.Bitmap?, callback: TokenCallback)
    
    /**
     * MTMD ASR Streaming Transcription with Dynamic Prompting
     */
    external fun transcribeStream(ctxPtr: Long, mtmdPtr: Long, audioData: ByteArray, prompt: String, temperature: Float, callback: TokenCallback)

    external fun loadVisionModel(projectorPath: String, textModelPtr: Long): Long
    external fun freeVisionModel(mtmdPtr: Long)
    
    external fun freeModel(modelPtr: Long)
    external fun freeContext(ctxPtr: Long)
    external fun init(nativeLibDir: String)
    external fun stop()

    /**
     * Accurate token counter using the model's own vocab
     */
    external fun countTokens(modelPtr: Long, text: String): Int

    /**
     * Returns the number of tokens currently in the KV Cache
     */
    external fun getKvCacheTokenCount(ctxPtr: Long): Int
}

/**
 * Callback for real-time token delivery from native layer
 */
interface TokenCallback {
    fun onBytes(bytes: ByteArray)
    fun onProgress(progress: Int)
    fun onSpeedUpdate(tps: Float)
}
