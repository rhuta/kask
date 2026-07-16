package com.rhuta.kask.domain.engine

import android.net.Uri
import com.rhuta.kask.domain.model.ChatMessage
import com.rhuta.kask.domain.model.TaskAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Core AI engine interface for on-device LLM inference.
 */
interface AIEngine {
    val isBusy: StateFlow<Boolean>
    val isQueued: StateFlow<Boolean>
    val generationSpeed: StateFlow<Float>
    val contextUsage: StateFlow<Pair<Int, Int>>

    fun processAction(
        action: TaskAction,
        input: String,
        uri: Uri?,
        params: Map<String, String>,
        history: List<ChatMessage> = emptyList()
    ): Flow<StreamResult>

    fun rewrite(text: String): Flow<StreamResult>
    fun summarize(text: String): Flow<StreamResult>
    fun extract(text: String): Flow<StreamResult>
    fun grammar(text: String): Flow<StreamResult>
    fun translate(text: String, targetLanguage: String = "English"): Flow<StreamResult>
    fun transcribe(audioFile: File): Flow<StreamResult>
    fun transcribeAudio(audioUri: Uri): Flow<StreamResult>
    fun transcribeAudio(audioUri: Uri, systemPrompt: String, userQuery: String): Flow<StreamResult>
    
    // Additional methods for convenience
    fun changeTone(text: String, tone: String): Flow<StreamResult>
    fun answerFromContext(question: String, context: String): Flow<StreamResult>
    fun generateText(prompt: String): Flow<StreamResult>
    fun generateChat(messages: List<ChatMessage>): Flow<StreamResult>
    
    // Multi-modal Vision support
    fun analyzeImage(imageUri: Uri, systemPrompt: String, userQuery: String): Flow<StreamResult>

    /**
     * Cancels any ongoing generation.
     */
    fun stop()

    /**
     * Releases resources.
     */
    fun release()

    /**
     * Accurate token counter
     */
    fun countTokens(text: String): Int
}

/**
 * Sealed class representing a token or an error in the stream.
 */
sealed class StreamResult {
    data class Token(val text: String) : StreamResult()
    object Thinking : StreamResult()
    data class Progress(val percentage: Int) : StreamResult()
    data class Speed(val tps: Float) : StreamResult()
    data class Error(val message: String, val cause: Throwable? = null) : StreamResult()
    object Complete : StreamResult()
}
