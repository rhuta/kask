package com.rhuta.kask.domain.engine

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StubAIEngine @Inject constructor() : AIEngine {

    private val _isBusy = MutableStateFlow(false)
    override val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _isQueued = MutableStateFlow(false)
    override val isQueued: StateFlow<Boolean> = _isQueued.asStateFlow()

    private val _generationSpeed = MutableStateFlow(0f)
    override val generationSpeed: StateFlow<Float> = _generationSpeed.asStateFlow()

    private val _contextUsage = MutableStateFlow(0 to 4096)
    override val contextUsage: StateFlow<Pair<Int, Int>> = _contextUsage.asStateFlow()

    override fun processAction(
        action: com.rhuta.kask.domain.model.TaskAction,
        input: String,
        uri: android.net.Uri?,
        params: Map<String, String>,
        history: List<com.rhuta.kask.domain.model.ChatMessage>
    ): Flow<StreamResult> = flow {
        _isBusy.value = true
        emit(StreamResult.Token("Stub processing $action for $input with uri $uri "))
        delay(1000)
        emit(StreamResult.Complete)
        _isBusy.value = false
    }

    override fun rewrite(text: String): Flow<StreamResult> = stubStream("Rewriting: $text")
    override fun summarize(text: String): Flow<StreamResult> = stubStream("Summarizing: $text")
    override fun extract(text: String): Flow<StreamResult> = stubStream("Extracting: $text")
    override fun grammar(text: String): Flow<StreamResult> = stubStream("Fixing grammar: $text")
    override fun translate(text: String, targetLanguage: String): Flow<StreamResult> = 
        stubStream("Translating to $targetLanguage: $text")
    override fun transcribe(audioFile: File): Flow<StreamResult> = 
        stubStream("Transcribed text from ${audioFile.name}")
    override fun transcribeAudio(audioUri: android.net.Uri): Flow<StreamResult> = 
        stubStream("Transcribed text from $audioUri")
    override fun transcribeAudio(audioUri: android.net.Uri, systemPrompt: String, userQuery: String): Flow<StreamResult> =
        stubStream("Transcribed text from $audioUri")
    override fun changeTone(text: String, tone: String): Flow<StreamResult> = 
        stubStream("Changing tone to $tone: $text")
    override fun answerFromContext(question: String, context: String): Flow<StreamResult> = 
        stubStream("Answering $question from context: $context")
    override fun generateText(prompt: String): Flow<StreamResult> = stubStream("Generating: $prompt")
    override fun generateChat(messages: List<com.rhuta.kask.domain.model.ChatMessage>): Flow<StreamResult> = 
        stubStream("Chatting with ${messages.size} messages")

    override fun analyzeImage(imageUri: android.net.Uri, systemPrompt: String, userQuery: String): Flow<StreamResult> = 
        stubStream("Stub analysis for image ${imageUri.lastPathSegment}: $userQuery")

    override fun stop() {
        _isBusy.value = false
    }

    override fun release() {}

    override fun countTokens(text: String): Int = text.length / 4

    private fun stubStream(message: String): Flow<StreamResult> = flow {
        _isBusy.value = true
        val words = message.split(" ")
        for (word in words) {
            emit(StreamResult.Token("$word "))
            delay(100)
        }
        emit(StreamResult.Complete)
        _isBusy.value = false
    }
}
