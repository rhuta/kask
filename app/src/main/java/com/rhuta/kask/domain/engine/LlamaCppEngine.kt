package com.rhuta.kask.domain.engine

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.rhuta.kask.domain.model.ChatMessage
import com.rhuta.kask.domain.model.EngineTier
import com.rhuta.kask.domain.model.ModelManager
import com.rhuta.kask.domain.model.TaskAction
import com.rhuta.kask.domain.util.TextExtractor
import com.rhuta.kask.ui.settings.PrefKeys
import com.rhuta.kask.ui.settings.settingsDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI Engine Implementation
 * Exactly synchronized with AINI project for professional file processing and real-time streaming.
 */
@Singleton
class LlamaCppEngine @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelManager: ModelManager,
) : AIEngine {

    private val bridge = LlamaBridge()
    private val textExtractor = TextExtractor(context)
    private val inferenceMutex = Mutex()
    
    private val aiDispatcher = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "Kask-AI-Thread")
    }.asCoroutineDispatcher()

    private val _isBusy = MutableStateFlow(value = false)
    override val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _isQueued = MutableStateFlow(value = false)
    override val isQueued: StateFlow<Boolean> = _isQueued.asStateFlow()

    private val _generationSpeed = MutableStateFlow(0f)
    override val generationSpeed: StateFlow<Float> = _generationSpeed.asStateFlow()

    private val _contextUsage = MutableStateFlow(0 to 8192)
    override val contextUsage: StateFlow<Pair<Int, Int>> = _contextUsage.asStateFlow()

    private var modelPtr: Long = 0L
    private var ctxPtr: Long = 0L
    private var mtmdPtr: Long = 0L
    
    private enum class LoadedModel { NONE, TEXT, ASR }
    private var loadedModelType = LoadedModel.NONE
    private var loadedModelPath: String = ""

    private val tempPrecision = 0.2f
    private val tempBalanced = 0.7f

    override fun rewrite(text: String) = processAction(TaskAction.REWRITE, text, null, emptyMap())
    override fun summarize(text: String) = processAction(TaskAction.SUMMARIZE, text, null, emptyMap())
    override fun extract(text: String) = processAction(TaskAction.EXTRACT, text, null, emptyMap())
    override fun grammar(text: String) = processAction(TaskAction.FIX_GRAMMAR, text, null, emptyMap())
    override fun translate(text: String, targetLanguage: String) = 
        processAction(TaskAction.TRANSLATE, text, null, mapOf("option" to targetLanguage))
    
    override fun changeTone(text: String, tone: String) = 
        processAction(TaskAction.CHANGE_TONE, text, null, mapOf("option" to tone))
    
    override fun answerFromContext(question: String, context: String) = 
        processAction(TaskAction.ASK_QUESTION, question, null, mapOf("context" to context))

    override fun processAction(
        action: TaskAction,
        input: String,
        uri: Uri?,
        params: Map<String, String>,
        history: List<ChatMessage>
    ): Flow<StreamResult> {
        var mimeType = uri?.let { context.contentResolver.getType(it) } ?: ""
        
        if ((mimeType.isEmpty()) && (uri?.scheme == "file")) {
            val path = uri.path?.lowercase() ?: ""
            mimeType = when {
                path.endsWith(".pcm") || path.endsWith(".wav") || path.endsWith(".mp3") -> "audio/raw"
                path.endsWith(".jpg") || path.endsWith(".jpeg") || path.endsWith(".png") -> "image/jpeg"
                else -> ""
            }
        }

        val actionPrompt = getSystemPromptFor(action, params)
        Log.d("Kask_AI", "processAction: $action, MimeType: $mimeType")
        
        return when {
            mimeType.startsWith("audio") -> {
                // PHASED AUDIO: 1. Transcribe (ASR), then 2. Process (LLM)
                channelFlow {
                val fullTranscript = StringBuilder()
                val flow = transcribeAudio(uri!!, "Transcribe this audio accurately.", "")
                
                var transcriptionReceived = false
                flow.collect { result ->
                    if (result is StreamResult.Token) {
                        transcriptionReceived = true
                        fullTranscript.append(result.text)
                        send(result)
                    } else if ((result is StreamResult.Speed) || (result is StreamResult.Progress) || result is StreamResult.Thinking) {
                        send(result)
                    }
                }

                if (!transcriptionReceived) {
                    send(StreamResult.Error("No speech detected or engine failure."))
                    send(StreamResult.Complete)
                    return@channelFlow
                }

                val rawTranscript = fullTranscript.toString()
                // Robust cleaning: removes language tags, separators and the echoed prompt
                val cleanTranscript = rawTranscript
                    .replace(Regex("<asr_text>|language\\s+\\w+|<\\|\\w+\\|>", RegexOption.IGNORE_CASE), "")
                    .replace("Transcribe this audio accurately.", "", ignoreCase = true)
                    .trim()

                if (cleanTranscript.isBlank()) {
                    send(StreamResult.Complete)
                    return@channelFlow
                }

                if (action == TaskAction.TRANSCRIBE) {
                    send(StreamResult.Complete)
                    return@channelFlow
                }

                if (action != TaskAction.FREE_FORM) {
                        send(StreamResult.Token("\n\n---\n\n**Action Result:**\n"))
                        val messages = listOf(ChatMessage("system", actionPrompt)) + history + ChatMessage("user", cleanTranscript)
                        generateTextInternal(messages, tempPrecision).collect { send(it) }
                    } else if (input.isNotBlank()) {
                        send(StreamResult.Token("\n\n---\n\n**Assistant:**\n"))
                        val messages = listOf(ChatMessage("system", "Use the provided transcript to fulfill the user request.")) + 
                                       history + 
                                       ChatMessage("user", "TRANSCRIPT:\n$cleanTranscript\n\nUSER REQUEST: $input")
                        generateTextInternal(messages, tempBalanced).collect { send(it) }
                    }
                    send(StreamResult.Complete)
                }
            }
            
            mimeType.startsWith("image") -> analyzeImage(uri!!, actionPrompt, input)
            
            else -> channelFlow {
                val contextText = if (uri != null) textExtractor.extractText(uri) else ""
                val messages = mutableListOf<ChatMessage>()
                
                // Only add the action system prompt if history doesn't already start with one
                if (history.none { it.role == "system" }) {
                    messages.add(ChatMessage("system", actionPrompt))
                }
                
                // Add existing history
                messages.addAll(history)

                if (contextText.isNotEmpty()) {
                    messages.add(ChatMessage("user", "CONTEXT:\n$contextText\n\nUSER REQUEST: $input"))
                } else if (input.isNotBlank()) {
                    messages.add(ChatMessage("user", input))
                } else if (uri != null) {
                    messages.add(ChatMessage("user", "Process the attached content."))
                }

                generateTextInternal(messages, if (action == TaskAction.ASK_QUESTION) tempBalanced else tempPrecision)
                    .collect { send(it) }
            }
        }
    }

    private fun getSystemPromptFor(action: TaskAction, params: Map<String, String>): String {
        val option = params["option"] ?: "English"
        val basePrompt = when(action) {
            TaskAction.SUMMARIZE -> "Summarize the following content into 3-5 concise bullet points of key facts. Use Markdown."
            TaskAction.DESCRIBE -> "Describe the contents of this image in detail. Use Markdown."
            TaskAction.EXTRACT -> "Extract and transcribe all key information, symbols, or text accurately. Maintain original structure and use Markdown."
            TaskAction.OCR -> "Transcribe all text from this image into Markdown. Output ONLY the transcription."
            TaskAction.REWRITE -> "Rewrite this text for better flow and clarity while maintaining the original meaning."
            TaskAction.TRANSLATE -> "You are a professional translator. Translate the text into $option. Output ONLY the translation."
            TaskAction.FIX_GRAMMAR -> "Fix the grammar and spelling errors in the following text. Output ONLY the corrected text."
            TaskAction.CHANGE_TONE -> "Rewrite the following text in a $option tone. Output ONLY the result."
            TaskAction.ASK_QUESTION -> "Use the provided context to answer the user's question accurately. If the answer isn't in the context, say you don't know."
            TaskAction.FREE_FORM -> "You are a helpful assistant. Provide direct and accurate responses."
            else -> "Process the following content accurately and concisely."
        }
        return "$basePrompt\nOutput ONLY the direct response. Do not include internal reasoning or thinking tags."
    }

    private fun applyNativeTemplate(messages: List<ChatMessage>, addAssistantSlot: Boolean = true): String {
        if (modelPtr == 0L) return ""
        val roles = messages.map { it.role }.toTypedArray()
        val contents = messages.map { it.content }.toTypedArray()
        return bridge.applyChatTemplate(modelPtr, roles, contents, addAssistantSlot)
    }

    override fun generateChat(messages: List<ChatMessage>): Flow<StreamResult> = generateTextInternal(messages, tempBalanced)

    override fun generateText(prompt: String): Flow<StreamResult> = 
        generateTextInternal(listOf(ChatMessage("user", prompt)), tempBalanced)

    private fun generateTextInternal(messages: List<ChatMessage>, temp: Float): Flow<StreamResult> = callbackFlow {
        try {
            inferenceMutex.withLock {
                _isBusy.value = true
                
                val prefs = context.settingsDataStore.data.first()
                val isPro = prefs[PrefKeys.IS_PRO_USER] ?: false
                val preferredName = prefs[PrefKeys.PREFERRED_MODEL] ?: "auto"
                val resolvedTier = modelManager.getResolvedTier(isPro, preferredName)

                if (!ensureModelLoaded(LoadedModel.TEXT, resolvedTier)) {
                    trySend(StreamResult.Error("Engine failed: Text model not found"))
                    close()
                    return@withLock
                }
                
                val constrainedHistory = getConstrainedMessages(messages)
                val prompt = applyNativeTemplate(constrainedHistory)
                
                collectBridgeStream(this) { callback ->
                    bridge.completionStream(ctxPtr, prompt, temp, callback)
                }
                
                updateContextUsage()
            }
        } catch (e: Exception) {
            trySend(StreamResult.Error(e.message ?: "Inference error"))
        } finally {
            _isBusy.value = false
            close()
        }
        awaitClose()
    }.flowOn(aiDispatcher).buffer(capacity = Channel.UNLIMITED)

    override fun transcribe(audioFile: File): Flow<StreamResult> = transcribeAudio(Uri.fromFile(audioFile))

    override fun transcribeAudio(audioUri: Uri): Flow<StreamResult> = transcribeAudio(audioUri, "Transcribe this audio.", "")

    override fun transcribeAudio(audioUri: Uri, systemPrompt: String, userQuery: String): Flow<StreamResult> = callbackFlow {
        try {
            inferenceMutex.withLock {
                _isBusy.value = true
                if (!ensureModelLoaded(LoadedModel.ASR, EngineTier.EFFICIENT)) {
                    trySend(StreamResult.Error("Engine failed: ASR model missing"))
                    close()
                    return@withLock
                }
                val audioBytes = context.contentResolver.openInputStream(audioUri)?.use { it.readBytes() }
                if ((audioBytes == null) || (audioBytes.isEmpty())) {
                    trySend(StreamResult.Error("Failed to read audio data"))
                    close()
                    return@withLock
                }

                val marker = bridge.getMarker()
                val content = if (userQuery.isNotBlank()) {
                    "$marker\n$systemPrompt\n\nUSER REQUEST: $userQuery"
                } else {
                    "$marker\n$systemPrompt"
                }
                
                val prompt = applyNativeTemplate(listOf(ChatMessage("user", content)))

                collectBridgeStream(this) { callback ->
                    bridge.transcribeStream(ctxPtr, mtmdPtr, audioBytes, prompt, 0.4f, callback)
                }
                
                updateContextUsage()
            }
        } catch (e: Exception) {
            trySend(StreamResult.Error(e.message ?: "ASR error"))
        } finally {
            _isBusy.value = false
            close()
        }
        awaitClose()
    }.flowOn(aiDispatcher).buffer(capacity = Channel.UNLIMITED)

    override fun analyzeImage(imageUri: Uri, systemPrompt: String, userQuery: String): Flow<StreamResult> = callbackFlow {
        try {
            inferenceMutex.withLock {
                _isBusy.value = true
                
                val prefs = context.settingsDataStore.data.first()
                val isPro = prefs[PrefKeys.IS_PRO_USER] ?: false
                val preferredName = prefs[PrefKeys.PREFERRED_MODEL] ?: "auto"
                val resolvedTier = modelManager.getResolvedTier(isPro, preferredName)

                if (!ensureModelLoaded(LoadedModel.TEXT, resolvedTier)) {
                    trySend(StreamResult.Error("Engine failed: Text model not found"))
                    close()
                    return@withLock
                }
                
                val bitmap = withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(imageUri)?.use {
                        val original = BitmapFactory.decodeStream(it) ?: return@use null
                        val maxDim = 1024
                        if (original.width > maxDim || original.height > maxDim) {
                            val ratio = original.width.toFloat() / original.height.toFloat()
                            val (w, h) = if (ratio > 1) maxDim to (maxDim / ratio).toInt() else (maxDim * ratio).toInt() to maxDim
                            Bitmap.createScaledBitmap(original, w, h, true)
                        } else original
                    }
                }

                if (bitmap == null) {
                    trySend(StreamResult.Error("Failed to decode image"))
                    close()
                    return@withLock
                }

                if (!loadVisionProjector(resolvedTier)) {
                    trySend(StreamResult.Error("Failed to load vision projector"))
                    close()
                    return@withLock
                }

                val marker = bridge.getMarker()
                val safeUserQuery = userQuery.ifBlank {
                    "Describe the contents of this image in plain text."
                }

                val messages = listOf(
                    ChatMessage("system", systemPrompt),
                    ChatMessage("user", "$marker\n$safeUserQuery")
                )
                val prompt = applyNativeTemplate(messages)

                collectBridgeStream(this) { callback ->
                    bridge.multimodalCompletionStream(ctxPtr, mtmdPtr, prompt, tempBalanced, bitmap, callback)
                }
                
                bitmap.recycle()
                updateContextUsage()
            }
        } catch (e: Exception) {
            trySend(StreamResult.Error(e.message ?: "Vision error"))
        } finally {
            _isBusy.value = false
            close()
        }
        awaitClose()
    }.flowOn(aiDispatcher).buffer(capacity = Channel.UNLIMITED)

    private suspend fun collectBridgeStream(
        scope: ProducerScope<StreamResult>,
        block: (TokenCallback) -> Unit,
    ) {
        val filter = ThinkingStateFilter(
            onToken = { scope.trySend(StreamResult.Token(it)) },
            onThinkingToken = { scope.trySend(StreamResult.Thinking) }
        )
        val decoder = BufferedUTF8Decoder { filter.onTextReceived(it) }

        block(
            object : TokenCallback {
                override fun onBytes(bytes: ByteArray) { decoder.decode(bytes) }
                override fun onProgress(progress: Int) { scope.trySend(StreamResult.Progress(progress)) }
                override fun onSpeedUpdate(tps: Float) { 
                    _generationSpeed.value = tps
                    scope.trySend(StreamResult.Speed(tps))
                }
            }
        )

        decoder.flush()
        filter.flush()
        scope.send(StreamResult.Complete)
    }

    override fun stop() {
        bridge.stop()
        _isBusy.value = false
    }

    override fun release() {
        if (mtmdPtr != 0L) { bridge.freeVisionModel(mtmdPtr) ; mtmdPtr = 0 }
        if (ctxPtr != 0L) { bridge.freeContext(ctxPtr) ; ctxPtr = 0 }
        if (modelPtr != 0L) { bridge.freeModel(modelPtr) ; modelPtr = 0 }
        loadedModelType = LoadedModel.NONE
        loadedModelPath = ""
    }

    private fun updateContextUsage() {
        if (ctxPtr != 0L) {
            val used = bridge.getKvCacheTokenCount(ctxPtr)
            _contextUsage.value = used.coerceIn(0, 8192) to 8192
        }
    }

    override fun countTokens(text: String): Int {
        return if (modelPtr != 0L) bridge.countTokens(modelPtr, text) else text.length / 4
    }

    /**
     * Implements a Sliding Window to ensure long conversations don't exceed context limits.
     * Reserves space for System Prompt and New Input, using the rest for History.
     */
    private fun getConstrainedMessages(history: List<ChatMessage>): List<ChatMessage> {
        if (modelPtr == 0L || history.isEmpty()) return history
        
        val maxTotalTokens = 8192
        val reserveForInput = 1024
        val budget = maxTotalTokens - reserveForInput
        
        val result = mutableListOf<ChatMessage>()
        var currentTokens = 0
        
        // 1. Separate System messages (always keep) and Chat history
        val systemMessages = history.filter { it.role == "system" }
        val chatMessages = history.filter { it.role != "system" }
        
        for (msg in systemMessages) {
            currentTokens += bridge.countTokens(modelPtr, msg.content)
            result.add(msg)
        }
        
        // 2. Add chat history from NEWEST to OLDEST until budget reached
        val recentChat = mutableListOf<ChatMessage>()
        for (i in chatMessages.indices.reversed()) {
            val msg = chatMessages[i]
            val msgTokens = bridge.countTokens(modelPtr, msg.content)
            if (currentTokens + msgTokens > budget) break
            
            recentChat.add(0, msg)
            currentTokens += msgTokens
        }
        
        result.addAll(recentChat)
        Log.d("Kask_AI", "Sliding window: history kept ${result.size}/${history.size} messages ($currentTokens tokens)")
        return result
    }

    private suspend fun ensureModelLoaded(type: LoadedModel, tier: EngineTier): Boolean = withContext(Dispatchers.IO) {
        val targetPath = if (type == LoadedModel.TEXT) {
            modelManager.getRecommendedModelFile(tier).absolutePath
        } else {
            modelManager.asrModelFile.absolutePath
        }

        if (loadedModelType == type && modelPtr != 0L && loadedModelPath == targetPath) {
             return@withContext true
        }
        
        release()
        
        when (type) {
            LoadedModel.TEXT -> loadTextModel(tier)
            LoadedModel.ASR -> loadAsrModel()
            else -> false
        }
    }

    private fun loadTextModel(tier: EngineTier): Boolean {
        if (!modelManager.isTextModelDownloaded(tier)) return false
        val file = modelManager.getRecommendedModelFile(tier)
        val path = file.absolutePath
        if (!file.exists()) return false
        
        modelPtr = bridge.loadModel(path)
        if (modelPtr == 0L) return false
        val nHeads = bridge.getMtpHeads(modelPtr)
        ctxPtr = bridge.createContext(modelPtr, nCtx = 8192, nBatch = 512, useMtp = (nHeads > 0))
        if (ctxPtr == 0L) return false
        loadedModelType = LoadedModel.TEXT
        loadedModelPath = path
        return true
    }

    private fun loadAsrModel(): Boolean {
        if (!modelManager.isWhisperDownloaded() || !modelManager.isAsrProjectorDownloaded()) return false
        val modelPath = modelManager.asrModelFile.absolutePath
        modelPtr = bridge.loadModel(modelPath)
        if (modelPtr == 0L) return false
        val projPath = modelManager.asrProjectorFile.absolutePath
        mtmdPtr = bridge.loadVisionModel(projPath, modelPtr)
        ctxPtr = bridge.createContext(modelPtr, nCtx = 8192, nBatch = 512, useMtp = false)
        loadedModelType = LoadedModel.ASR
        loadedModelPath = modelPath
        return true
    }

    private fun loadVisionProjector(tier: EngineTier): Boolean {
        if (modelPtr == 0L || loadedModelType != LoadedModel.TEXT) return false
        val path = modelManager.getRecommendedVisionFile(tier).absolutePath
        mtmdPtr = bridge.loadVisionModel(path, modelPtr)
        return mtmdPtr != 0L
    }

    private class ThinkingStateFilter(
        val onToken: (String) -> Unit,
        val onThinkingToken: () -> Unit = {}
    ) {
        private var isThinking = false
        private var hasContentStarted = false
        private val buffer = StringBuilder()

        fun onTextReceived(text: String) {
            if (text.isEmpty()) return
            buffer.append(text)

            while (buffer.isNotEmpty()) {
                if (!isThinking) {
                    val openIdx = buffer.indexOf("<think")
                    if (openIdx != -1) {
                        val tagEnd = buffer.indexOf(">", openIdx)
                        if (tagEnd != -1) {
                            val before = buffer.substring(0, openIdx)
                            if (before.isNotEmpty()) emitToken(before)
                            isThinking = true
                            onThinkingToken()
                            buffer.delete(0, tagEnd + 1)
                        } else {
                            val before = buffer.substring(0, openIdx)
                            if (before.isNotEmpty()) emitToken(before)
                            buffer.delete(0, openIdx)
                            break
                        }
                    } else {
                        val lastBracket = buffer.lastIndexOf("<")
                        if (lastBracket != -1 && "<think".startsWith(buffer.substring(lastBracket))) {
                            val before = buffer.substring(0, lastBracket)
                            if (before.isNotEmpty()) emitToken(before)
                            buffer.delete(0, lastBracket)
                            break
                        } else {
                            emitToken(buffer.toString())
                            buffer.setLength(0)
                        }
                    }
                } else {
                    val closeIdx = buffer.indexOf("</think")
                    if (closeIdx != -1) {
                        val tagEnd = buffer.indexOf(">", closeIdx)
                        if (tagEnd != -1) {
                            isThinking = false
                            onThinkingToken()
                            buffer.delete(0, tagEnd + 1)
                        } else {
                            buffer.delete(0, closeIdx)
                            break
                        }
                    } else {
                        val lastBracket = buffer.lastIndexOf("<")
                        if (lastBracket != -1 && "</think".startsWith(buffer.substring(lastBracket))) {
                            onThinkingToken()
                            buffer.delete(0, lastBracket)
                            break
                        } else {
                            onThinkingToken()
                            buffer.setLength(0)
                        }
                    }
                }
            }
        }

        private fun emitToken(token: String) {
            var cleanToken = token
            if (!hasContentStarted) {
                val firstChar = cleanToken.indexOfFirst { !it.isWhitespace() }
                if (firstChar == -1) return
                cleanToken = cleanToken.substring(firstChar)
                hasContentStarted = true
            }
            if (cleanToken.isNotEmpty()) onToken(cleanToken)
        }

        fun flush() {
            if (buffer.isNotEmpty() && !isThinking) {
                emitToken(buffer.toString())
            }
            buffer.setLength(0)
        }
    }

    private class BufferedUTF8Decoder(val onText: (String) -> Unit) {
        private val decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPLACE)
            .onUnmappableCharacter(CodingErrorAction.REPLACE)
        private val byteBuffer = ByteBuffer.allocate(4096)
        private val charBuffer = CharBuffer.allocate(4096)

        fun decode(bytes: ByteArray) {
            var offset = 0
            while (offset < bytes.size) {
                val len = minOf(bytes.size - offset, byteBuffer.remaining())
                byteBuffer.put(bytes, offset, len)
                offset += len
                byteBuffer.flip()
                decoder.decode(byteBuffer, charBuffer, false)
                byteBuffer.compact()
                charBuffer.flip()
                if (charBuffer.hasRemaining()) {
                    onText(charBuffer.toString())
                }
                charBuffer.clear()
            }
        }

        fun flush() {
            byteBuffer.flip()
            decoder.decode(byteBuffer, charBuffer, true)
            decoder.flush(charBuffer)
            charBuffer.flip()
            if (charBuffer.hasRemaining()) {
                onText(charBuffer.toString())
            }
            byteBuffer.clear()
            charBuffer.clear()
        }
    }
}
