package com.rhuta.kask.ui.home

import android.app.ActivityManager
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rhuta.kask.data.db.entities.HistoryEntity
import com.rhuta.kask.data.repository.KaskRepository
import com.rhuta.kask.domain.audio.AudioRecorder
import com.rhuta.kask.domain.engine.AIEngine
import com.rhuta.kask.domain.engine.StreamResult
import com.rhuta.kask.domain.model.*
import com.rhuta.kask.ui.settings.PrefKeys
import com.rhuta.kask.ui.settings.settingsDataStore
import com.rhuta.kask.domain.util.FileUtility
import com.rhuta.kask.domain.util.MediaTruncator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject

// ---- UI state -----------------------------------------------------------

data class HomeUiState(
    val inputText: String = "",
    val attachedUri: Uri? = null,
    val attachedFileName: String? = null,
    val detectedContentType: ContentType? = null,
    val availableActions: List<ActionChip> = emptyList(),
    val selectedAction: TaskAction? = null,
    val inferenceState: InferenceState = InferenceState.Idle,
    val streamingText: String = "",
    val isInputTooLong: Boolean = false,
    val currentConversation: Conversation = Conversation(),
    val isRecording: Boolean = false,
    val recordingStage: RecordingStage = RecordingStage.IDLE,
    val recordingDuration: Int = 0,
    val tokenSpeed: Float = 0f,
    val ramUsage: String = "",
    val deviceTemp: String = "",
)

sealed class InferenceState {
    object Idle : InferenceState()
    object Loading : InferenceState()
    object Streaming : InferenceState()
    data class Success(val result: String, val historyId: String) : InferenceState()
    data class Error(val message: String) : InferenceState()
}

// ---- Events from UI -----------------------------------------------------

sealed class HomeEvent {
    data class TextChanged(val text: String) : HomeEvent()
    data class FileAttached(val uri: Uri, val mimeType: String, val fileName: String) : HomeEvent()
    data class ActionSelected(val chip: ActionChip) : HomeEvent()
    object ClearAttachment : HomeEvent()
    object ClearAll : HomeEvent()
    object StopInference : HomeEvent()
    object ToggleRecording : HomeEvent()
    object StopRecording : HomeEvent()
    object RedoRecording : HomeEvent()
    object ConfirmRecording : HomeEvent()
    object CancelRecording : HomeEvent()
    data class ContinueChat(val historyId: String) : HomeEvent()
}

// ---- ViewModel ----------------------------------------------------------

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val engine: AIEngine,
    private val repository: KaskRepository,
    private val audioRecorder: AudioRecorder,
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var recordingFile: File? = null

    init {
        startRamUsageTracking()
    }

    private fun startRamUsageTracking() {
        viewModelScope.launch {
            // Temperature tracking via Broadcast
            val tempFlow = callbackFlow {
                val receiver = object : android.content.BroadcastReceiver() {
                    override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
                        val temp = intent?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0
                        trySend(if (temp > 0) "${temp / 10}°C" else "")
                    }
                }
                context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                awaitClose { context.unregisterReceiver(receiver) }
            }

            tempFlow.onEach { temp ->
                _uiState.update { it.copy(deviceTemp = temp) }
            }.launchIn(this)

            while (true) {
                val ram = getRamUsage()
                _uiState.update { it.copy(ramUsage = ram) }
                kotlinx.coroutines.delay(5000) // Increased delay for RAM
            }
        }
    }

    private fun getRamUsage(): String {
        val activityManager = context.getSystemService(android.content.Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memInfo)
        val usedMem = (memInfo.totalMem - memInfo.availMem) / (1024 * 1024 * 1024.0)
        val totalMem = memInfo.totalMem / (1024 * 1024 * 1024.0)
        return "%.1f/%.1f GB".format(usedMem, totalMem)
    }

    fun onEvent(event: HomeEvent) {
        when (event) {
            is HomeEvent.TextChanged -> handleTextChanged(event.text)
            is HomeEvent.FileAttached -> handleFileAttached(event.uri, event.mimeType, event.fileName)
            is HomeEvent.ActionSelected -> handleActionSelected(event.chip)
            is HomeEvent.ClearAttachment -> clearAttachment()
            is HomeEvent.ClearAll -> {
                engine.release()
                clearAll()
                FileUtility.clearUploadCache(context)
            }
            is HomeEvent.StopInference -> engine.stop()
            is HomeEvent.ContinueChat -> handleContinueChat(event.historyId)
            is HomeEvent.ToggleRecording -> handleToggleRecording()
            is HomeEvent.StopRecording -> handleStopRecording()
            is HomeEvent.RedoRecording -> handleRedoRecording()
            is HomeEvent.ConfirmRecording -> handleConfirmRecording()
            is HomeEvent.CancelRecording -> handleCancelRecording()
        }
    }

    private fun handleToggleRecording() {
        if (_uiState.value.isRecording) {
            handleStopRecording()
        } else {
            val file = FileUtility.createCacheFile(context, "wav")
            recordingFile = file
            audioRecorder.start(file)
            _uiState.update { it.copy(isRecording = true, recordingStage = RecordingStage.RECORDING, recordingDuration = 0) }
            startTimer()
        }
    }

    private fun handleStopRecording() {
        audioRecorder.stop()
        _uiState.update { it.copy(isRecording = false, recordingStage = RecordingStage.REVIEW) }
        stopTimer()
    }

    private fun handleRedoRecording() {
        recordingFile?.delete()
        _uiState.update { it.copy(recordingStage = RecordingStage.IDLE, recordingDuration = 0) }
        handleToggleRecording()
    }

    private fun handleConfirmRecording() {
        val file = recordingFile ?: return
        handleFileAttached(Uri.fromFile(file), "audio/wav", file.name)
        _uiState.update { it.copy(recordingStage = RecordingStage.IDLE) }
    }

    private fun handleCancelRecording() {
        recordingFile?.delete()
        _uiState.update { it.copy(recordingStage = RecordingStage.IDLE, recordingDuration = 0) }
    }

    private var timerJob: kotlinx.coroutines.Job? = null
    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (true) {
                kotlinx.coroutines.delay(1000)
                val newDuration = _uiState.value.recordingDuration + 1
                _uiState.update { it.copy(recordingDuration = newDuration) }
            }
        }
    }

    private fun stopTimer() {
        timerJob?.cancel()
        timerJob = null
    }

    private fun handleContinueChat(historyId: String) {
        viewModelScope.launch {
            repository.getHistoryById(historyId)?.let { item ->
                val conversation = item.conversationJson?.let {
                    try {
                        Json.decodeFromString<Conversation>(it)
                    } catch (e: Exception) {
                        Conversation(messages = listOf(ChatMessage("assistant", item.fullOutput)))
                    }
                } ?: Conversation(messages = listOf(ChatMessage("assistant", item.fullOutput)))

                _uiState.update {
                    it.copy(
                        inputText = "", 
                        currentConversation = conversation
                    )
                }
            }
        }
    }

    private fun handleTextChanged(text: String) {
        // Accurate token counting using the engine
        val tokenCount = engine.countTokens(text)
        val isTooLong = tokenCount > 2000
        
        _uiState.update {
            it.copy(
                inputText = text,
                availableActions = if (it.attachedUri != null) it.availableActions else emptyList(),
                isInputTooLong = isTooLong
            )
        }
    }

    private fun handleFileAttached(uri: Uri, mimeType: String, fileName: String) {
        viewModelScope.launch {
            // RESOLVE REAL NAME: AINI uses OpenableColumns to avoid random names
            val realName = FileUtility.getFileName(context, uri) ?: fileName
            
            val type = when {
                mimeType.contains("pdf") || 
                mimeType.contains("text") || 
                mimeType.contains("wordprocessingml.document") ||
                realName.endsWith(".docx", ignoreCase = true) ||
                realName.endsWith(".pptx", ignoreCase = true) ||
                realName.endsWith(".xlsx", ignoreCase = true) -> ContentType.PDF
                mimeType.startsWith("image") || realName.endsWith(".jpg") || realName.endsWith(".jpeg") || realName.endsWith(".png") -> ContentType.IMAGE
                mimeType.startsWith("audio") || realName.endsWith(".wav") || realName.endsWith(".mp3") || realName.endsWith(".m4a") -> ContentType.AUDIO
                else -> ContentType.PDF
            }

            // STREAM TO STORAGE: Ensure stability (Always copy external or content URIs)
            var finalUri = uri
            val isInternal = uri.scheme == "file" && (uri.path?.startsWith(context.cacheDir.parent ?: "") == true)
            
            if (!isInternal) {
                withContext(Dispatchers.IO) {
                    // SPECIAL HANDLING: If it's an audio file, truncate to ensure engine stability
                    val sourceToCopy = if (type == ContentType.AUDIO && uri.scheme == "content") {
                        MediaTruncator.truncateAudio(context, uri, realName, 600)?.let { Uri.fromFile(it) } ?: uri
                    } else {
                        uri
                    }

                    // Ensure local file has correct extension for engine detection
                    val localFile = FileUtility.createCacheFileWithName(context, realName)
                    if (FileUtility.copyUriToLocalStorage(context, sourceToCopy, localFile)) {
                        finalUri = Uri.fromFile(localFile)
                    }
                }
            }

            _uiState.update {
                it.copy(
                    attachedUri = finalUri,
                    attachedFileName = realName,
                    detectedContentType = type,
                    availableActions = actionsFor(type)
                )
            }
        }
    }

    private fun handleActionSelected(chip: ActionChip) {
        // Correctly handle chip selection vs manual text entry
        // Manual Send uses TaskAction.FREE_FORM and passes current inputText
        runInference(chip.action, chip.defaultPrompt)
    }

    private fun runInference(
        action: TaskAction,
        input: String,
        params: Map<String, String> = emptyMap()
    ) {
        Log.d("Kask_Home", "UI: runInference requested for action: $action")
        if (input.isBlank() && action != TaskAction.TRANSCRIBE) return
        viewModelScope.launch {
            // Get default preferences
            val prefs = context.settingsDataStore.data.first()
            val responseLang = prefs[PrefKeys.APP_LANGUAGE] ?: "en"

            val currentState = _uiState.value
            val historyMessages = currentState.currentConversation.messages.takeLast(9)
            val currentType = currentState.detectedContentType ?: ContentType.TEXT
            
            // Sliding Window: Pin system message at index 0
            val systemMessage = ChatMessage(
                role = "system",
                content = "You are a helpful assistant. Provide direct responses in ${languageLabel(responseLang)}. Do not use a \"thinking\" process or internal monologue."
            )

            val userMessage = ChatMessage(
                role = "user",
                content = input,
                attachmentUri = currentState.attachedUri?.toString(),
                attachmentType = currentType.name.lowercase()
            )
            
            val updatedMessages = if (action == TaskAction.TRANSCRIBE) {
                // For Auto-transcribe, just show the file without system/user message bubbles
                listOf(userMessage)
            } else {
                historyMessages + userMessage
            }

            // Update UI immediately
            _uiState.update { 
                it.copy(
                    inferenceState = InferenceState.Loading, 
                    streamingText = "",
                    tokenSpeed = 0f,
                    currentConversation = it.currentConversation.copy(messages = updatedMessages),
                    inputText = "", 
                    availableActions = emptyList(),
                    selectedAction = null,
                    attachedUri = if (action == TaskAction.TRANSCRIBE) it.attachedUri else null, 
                    attachedFileName = if (action == TaskAction.TRANSCRIBE) it.attachedFileName else null
                ) 
            }

            val started = System.currentTimeMillis()
            val flow = engine.processAction(action, input, currentState.attachedUri, params, historyMessages)

            var tokenCount = 0
            val inferenceStart = System.currentTimeMillis()
            val fullTextBuilder = StringBuilder()
            var lastUpdate = 0L

            try {
                Log.d("Kask_Home", "Collecting inference flow...")
                flow.collect { result ->
                    when (result) {
                        is StreamResult.Token -> {
                            fullTextBuilder.append(result.text)
                            tokenCount++
                            
                            val fullText = fullTextBuilder.toString()
                            val now = System.currentTimeMillis()
                            val durationSeconds = (now - inferenceStart) / 1000f
                            val fallbackSpeed = if (durationSeconds > 0) tokenCount / durationSeconds else 0f
                            
                            // UI THROTTLE (100ms): Balanced for rich Markdown rendering
                            if (lastUpdate == 0L || now - lastUpdate > 100) {
                                _uiState.update { 
                                    it.copy(
                                        inferenceState = InferenceState.Streaming, 
                                        streamingText = fullText,
                                        tokenSpeed = if (it.tokenSpeed > 0) it.tokenSpeed else fallbackSpeed
                                    ) 
                                }
                                lastUpdate = now
                            }
                        }
                        is StreamResult.Thinking -> {
                            _uiState.update { it.copy(inferenceState = InferenceState.Loading) }
                        }
                        is StreamResult.Speed -> {
                            _uiState.update { it.copy(tokenSpeed = result.tps) }
                        }
                        is StreamResult.Complete -> {
                            val fullText = fullTextBuilder.toString()
                            val elapsed = System.currentTimeMillis() - started
                            
                            // Combine system message only for the actual inference request
                            val finalMessages = if (action == TaskAction.TRANSCRIBE) {
                                updatedMessages + ChatMessage("assistant", fullText)
                            } else {
                                listOf(systemMessage) + updatedMessages + ChatMessage("assistant", fullText)
                            }
                            
                            val limitedMessages = finalMessages.takeLast(11) // Keep up to 10 + system
                            val finalConversation = Conversation(limitedMessages)
                            
                            val historyId = saveHistory(
                                action = action,
                                input = input,
                                output = fullText,
                                elapsed = elapsed,
                                conversation = finalConversation,
                                contentType = currentType
                            )
                            _uiState.update {
                                it.copy(
                                    inferenceState = InferenceState.Success(fullText, historyId),
                                    currentConversation = finalConversation,
                                    attachedUri = if (action == TaskAction.TRANSCRIBE) it.attachedUri else null,
                                    attachedFileName = if (action == TaskAction.TRANSCRIBE) it.attachedFileName else null
                                )
                            }
                        }
                        is StreamResult.Error -> {
                            _uiState.update {
                                it.copy(
                                    inferenceState = InferenceState.Error(result.message)
                                )
                            }
                        }
                        else -> {}
                    }
                }
                Log.d("Kask_Home", "Flow collection finished normally")
            } catch (e: Exception) {
                Log.e("Kask_Home", "Flow collection failed", e)
                _uiState.update { it.copy(inferenceState = InferenceState.Error(e.message ?: "Unknown Error")) }
            } finally {
                // SAFETY: Ensure state is never stuck in Loading/Streaming if flow stops abruptly
                _uiState.update { state ->
                    if (state.inferenceState is InferenceState.Loading || state.inferenceState is InferenceState.Streaming) {
                        state.copy(inferenceState = InferenceState.Idle)
                    } else state
                }
            }
        }
    }

    private suspend fun saveHistory(
        action: TaskAction,
        input: String,
        output: String,
        elapsed: Long,
        conversation: Conversation? = null,
        contentType: ContentType = ContentType.TEXT
    ): String {
        val entry = HistoryEntity(
            contentType = contentType.name,
            taskAction = action.name,
            inputPreview = input.take(120),
            outputPreview = output.take(120),
            fullOutput = output,
            inputUri = _uiState.value.attachedUri?.toString(),
            conversationJson = conversation?.let { Json.encodeToString(it) },
            processingTimeMs = elapsed
        )
        repository.saveHistory(entry)
        return entry.id
    }

    private fun clearAttachment() {
        _uiState.update {
            it.copy(
                attachedUri = null,
                attachedFileName = null,
                detectedContentType = if (it.inputText.isNotBlank()) ContentType.TEXT else null,
                availableActions = emptyList()
            )
        }
    }

    private fun clearAll() {
        _uiState.update { HomeUiState() }
    }

    private fun languageLabel(code: String) = mapOf(
        "en" to "English", "es" to "Spanish", "fr" to "French",
        "ar" to "Arabic", "de" to "German", "ja" to "Japanese",
        "zh" to "Chinese", "pt" to "Portuguese", "hi" to "Hindi"
    )[code] ?: code
}
