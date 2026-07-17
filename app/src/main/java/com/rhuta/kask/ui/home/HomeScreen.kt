package com.rhuta.kask.ui.home

import android.Manifest
import android.net.Uri
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel as lifecycleHiltViewModel
import com.rhuta.kask.domain.model.ActionChip
import com.rhuta.kask.domain.model.ChatMessage
import com.rhuta.kask.domain.model.TaskAction
import com.rhuta.kask.domain.model.RecordingStage
import com.rhuta.kask.ui.settings.SettingsViewModel
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import coil.size.Scale
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.rhuta.kask.ui.markdown.MarkdownMessage

@OptIn(ExperimentalPermissionsApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = lifecycleHiltViewModel(),
    settingsViewModel: SettingsViewModel = lifecycleHiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val scrollState = rememberScrollState()
    val focusRequester = remember { FocusRequester() }
    var isMenuExpanded by remember { mutableStateOf(value = false) }

    // Check for pending chat context (from History screen)
    LaunchedEffect(Unit) {
        viewModel.onEvent(HomeEvent.CheckPendingChat)
    }

    // File picker
    val fileLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        uri?.let {
            val mime = context.contentResolver.getType(it) ?: "application/pdf"
            val name = it.lastPathSegment ?: "file"
            viewModel.onEvent(HomeEvent.FileAttached(it, mime, name))
        }
    }

    // Camera launcher
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicturePreview(),
    ) { bitmap ->
        if (bitmap != null) {
            val file = File(context.cacheDir, "camera_capture_${System.currentTimeMillis()}.jpg")
            try {
                file.outputStream().use { out ->
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, out)
                }
                val uri = Uri.fromFile(file)
                viewModel.onEvent(HomeEvent.FileAttached(uri, "image/jpeg", file.name))
            } catch (_: Exception) {
                // handle error
            }
        }
    }

    // Mic permission
    val micPermission = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
    // Camera permission
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    // Haptic feedback for inference events
    LaunchedEffect(uiState.inferenceState) {
        when (uiState.inferenceState) {
            is InferenceState.Loading -> {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
            }
            is InferenceState.Success -> {
                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
            }
            else -> {}
        }
    }

    // Auto-scroll to bottom
    LaunchedEffect(uiState.currentConversation.messages.size, uiState.streamingText, uiState.recordingStage) {
        scrollState.animateScrollTo(scrollState.maxValue)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "kask",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                        SystemStatsRow(
                            ramUsage = uiState.ramUsage,
                            tokenSpeed = uiState.tokenSpeed,
                            temperature = uiState.deviceTemp
                        )
                    }
                },
                actions = {
                    if (uiState.currentConversation.messages.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onEvent(HomeEvent.ClearAll) }) {
                            Icon(
                                imageVector = Icons.Outlined.DeleteForever,
                                contentDescription = "New Chat",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface
                ),
                windowInsets = WindowInsets.statusBars
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {

            if (uiState.recordingStage != RecordingStage.IDLE) {
                RecordingInterface(
                    stage = uiState.recordingStage,
                    duration = uiState.recordingDuration,
                    onStop = { viewModel.onEvent(HomeEvent.StopRecording) },
                    onRedo = { viewModel.onEvent(HomeEvent.RedoRecording) },
                    onConfirm = { viewModel.onEvent(HomeEvent.ConfirmRecording) },
                ) {
                    viewModel.onEvent(HomeEvent.CancelRecording)
                }
            } else {
                // ---- Conversation History -----------------------------------
                uiState.currentConversation.messages.filter { it.role != "system" }.forEach { message ->
                    ChatBubble(message, settingsState.chatFontSize, settingsState.darkMode)
                }

                // ---- Streaming / Loading response ---------------------------
                if (uiState.inferenceState is InferenceState.Loading) {
                    ThinkingIndicator()
                } else if (uiState.inferenceState is InferenceState.Streaming) {
                    ChatBubble(
                        ChatMessage(role = "assistant", content = uiState.streamingText),
                        settingsState.chatFontSize,
                        settingsState.darkMode
                    )
                }

                // ---- Empty / idle state: quick-start suggestions ------------
                if ((uiState.currentConversation.messages.isEmpty()) &&
                    (uiState.inferenceState is InferenceState.Idle)) {
                    QuickStartSuggestions(
                        onSuggestionClick = { suggestion ->
                            viewModel.onEvent(HomeEvent.TextChanged(suggestion.input))
                            when (suggestion.action) {
                                "pdf" -> fileLauncher.launch("application/pdf")
                                "image" -> cameraLauncher.launch(null)
                                "audio" -> {
                                    if (micPermission.status.isGranted) {
                                        viewModel.onEvent(HomeEvent.ToggleRecording)
                                    } else {
                                        micPermission.launchPermissionRequest()
                                    }
                                }
                                "text" -> focusRequester.requestFocus()
                            }
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // ---- Error state --------------------------------------------
            AnimatedVisibility(visible = uiState.inferenceState is InferenceState.Error) {
                val msg = (uiState.inferenceState as? InferenceState.Error)?.message ?: ""
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Text(
                        text = msg,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }

            // ---- Main input card ----------------------------------------
            if (uiState.recordingStage == RecordingStage.IDLE) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 2.dp
                ) {
                    Column(modifier = Modifier.padding(vertical = 4.dp, horizontal = 8.dp)) {

                        // Attachment pill
                        AnimatedVisibility(visible = uiState.attachedFileName != null) {
                            uiState.attachedFileName?.let { name ->
                                AttachmentPill(
                                    fileName = name,
                                    onRemove = { viewModel.onEvent(HomeEvent.ClearAttachment) },
                                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp, top = 4.dp)
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left: Expandable Menu
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.animateContentSize()
                            ) {
                                IconButton(
                                    onClick = { isMenuExpanded = !isMenuExpanded }
                                ) {
                                    Icon(
                                        imageVector = if (isMenuExpanded) Icons.Outlined.Close else Icons.Outlined.Add,
                                        contentDescription = "Menu",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }

                                AnimatedVisibility(
                                    visible = isMenuExpanded,
                                    enter = expandHorizontally() + fadeIn(),
                                    exit = shrinkHorizontally() + fadeOut()
                                ) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        // Voice
                                        IconButton(
                                            onClick = {
                                                isMenuExpanded = false
                                                if (micPermission.status.isGranted) {
                                                    viewModel.onEvent(HomeEvent.ToggleRecording)
                                                } else {
                                                    micPermission.launchPermissionRequest()
                                                }
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Outlined.Mic,
                                                contentDescription = "Voice",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        // Camera
                                        IconButton(
                                            onClick = {
                                                isMenuExpanded = false
                                                if (cameraPermission.status.isGranted) {
                                                    cameraLauncher.launch(null)
                                                } else {
                                                    cameraPermission.launchPermissionRequest()
                                                }
                                            }
                                        ) {
                                            Icon(Icons.Outlined.CameraAlt, contentDescription = "Camera", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        // Upload
                                        IconButton(
                                            onClick = {
                                                isMenuExpanded = false
                                                fileLauncher.launch("*/*")
                                            }
                                        ) {
                                            Icon(Icons.Outlined.AttachFile, contentDescription = "Upload", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                    }
                                }
                            }

                            // Center: Text input
                            TextField(
                                value = uiState.inputText,
                                onValueChange = {
                                    viewModel.onEvent(HomeEvent.TextChanged(it))
                                    if (it.isNotBlank()) isMenuExpanded = false
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(focusRequester)
                                    .defaultMinSize(minHeight = 48.dp),
                                placeholder = {
                                    Text(
                                        text = if (uiState.attachedFileName != null)
                                            "Ask about this file…"
                                        else
                                            "Message kask",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                    unfocusedContainerColor = androidx.compose.ui.graphics.Color.Transparent,
                                    focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                    unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                                    disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent
                                ),
                                textStyle = MaterialTheme.typography.bodyLarge,
                                isError = uiState.isInputTooLong
                            )

                            // Right: Send button
                            if ((uiState.inferenceState is InferenceState.Loading) ||
                                (uiState.inferenceState is InferenceState.Streaming)) {
                                FilledIconButton(
                                    onClick = { viewModel.onEvent(HomeEvent.StopInference) },
                                    colors = IconButtonDefaults.filledIconButtonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                ) {
                                    Icon(Icons.Outlined.Stop, contentDescription = "Stop")
                                }
                            } else {
                                FilledIconButton(
                                    onClick = {
                                        viewModel.onEvent(
                                            HomeEvent.ActionSelected(
                                                ActionChip(
                                                    TaskAction.FREE_FORM,
                                                    "Send",
                                                    emptySet(),
                                                    uiState.inputText
                                                )
                                            )
                                        )
                                        // Clear menu if open
                                        isMenuExpanded = false
                                    },
                                    enabled = (uiState.inputText.isNotBlank()) || (uiState.attachedUri != null)
                                ) {
                                    Icon(Icons.Outlined.ArrowUpward, contentDescription = "Send")
                                }
                            }
                        }

                        AnimatedVisibility(visible = uiState.isInputTooLong) {
                            Text(
                                text = "Input is very long. It may be truncated.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)
                            )
                        }
                    }
                }
            }

            // ---- Smart action chips -------------------------------------
            AnimatedVisibility(
                visible = (uiState.availableActions.isNotEmpty()) &&
                          (uiState.inferenceState == InferenceState.Idle) &&
                          (uiState.recordingStage == RecordingStage.IDLE),
                enter = fadeIn() + slideInVertically(),
                exit = fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Suggested actions",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(uiState.availableActions) { chip ->
                            ActionChipItem(
                                chip = chip,
                                isSelected = uiState.selectedAction == chip.action,
                                onClick = {
                                    viewModel.onEvent(HomeEvent.ActionSelected(chip))
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---- Sub-composables ----------------------------------------------------

@Composable
private fun SystemStatsRow(
    ramUsage: String,
    tokenSpeed: Float,
    temperature: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // Subtle Vertical Divider
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(16.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        )

        Spacer(modifier = Modifier.width(2.dp))

        if (temperature.isNotBlank()) {
            StatItem(
                icon = Icons.Outlined.DeviceThermostat,
                label = temperature
            )
        }

        if (ramUsage.isNotBlank()) {
            DotSeparator()
            StatItem(
                icon = Icons.Outlined.Memory,
                label = ramUsage
            )
        }

        // Show speed if it has ever been calculated (> 0)
        if (tokenSpeed > 0) {
            DotSeparator()
            StatItem(
                icon = Icons.Outlined.Speed,
                label = "%.0f t/s".format(tokenSpeed)
            )
        }
    }
}

@Composable
private fun DotSeparator() {
    Text(
        text = "•",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.outlineVariant,
        modifier = Modifier.padding(horizontal = 2.dp)
    )
}

@Composable
private fun StatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }
}

@Composable
private fun ThinkingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "thinking")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalAlignment = Alignment.Start
    ) {
        Surface(
            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
            shape = RoundedCornerShape(
                topStart = 16.dp,
                topEnd = 16.dp,
                bottomStart = 4.dp,
                bottomEnd = 16.dp
            ),
            modifier = Modifier.width(80.dp).height(24.dp).graphicsLayer(alpha = alpha)
        ) {
            // Skeleton pulsing indicator
        }
    }
}

@Composable
private fun ChatBubble(message: ChatMessage, fontSize: Float, isDark: Boolean) {
    val isUser = message.role == "user"
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        if (isUser) {
            Surface(
                color = MaterialTheme.colorScheme.primaryContainer,
                shape = RoundedCornerShape(
                    topStart = 20.dp,
                    topEnd = 20.dp,
                    bottomStart = 20.dp,
                    bottomEnd = 4.dp
                )
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    if (message.attachmentUri != null) {
                        AttachmentPreview(message)
                    }

                    if (message.content.isNotBlank()) {
                        SelectionContainer {
                            Text(
                                text = message.content,
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = fontSize.sp)
                            )
                        }
                    }
                }
            }
        } else {
            // Assistant: No bubble, just clean text/markdown
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.98f) // Increased width slightly for better reading
                    .padding(vertical = 4.dp)
            ) {
                if (message.content.isNotBlank()) {
                    SelectionContainer {
                        // ALWAYS use MarkdownMessage for rich rendering, even during streaming.
                        // Exactly synchronized with AINI for professional live formatting.
                        MarkdownMessage(
                            content = message.content,
                            isDark = isDark,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }
        // Spacing between messages
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
private fun AttachmentPreview(message: ChatMessage) {
    val ext = message.attachmentType?.lowercase() ?: ""
    val isImage = ext in listOf("jpg", "jpeg", "png", "webp", "image")

    if (isImage) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(message.attachmentUri)
                .crossfade(enable = true)
                .precision(Precision.EXACT)
                .scale(Scale.FILL)
                .build(),
            contentDescription = "Attached image",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(160.dp)
                .padding(bottom = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        )
    } else {
        Surface(
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Row(
                modifier = Modifier.padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = when (val type = message.attachmentType?.lowercase()) {
                        "pdf" -> Icons.Outlined.PictureAsPdf
                        "jpg", "jpeg", "png", "webp", "image" -> Icons.Outlined.Image
                        "wav", "mp3", "m4a", "audio" -> Icons.Outlined.AudioFile
                        else -> Icons.Outlined.Description
                    },
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Attached ${message.attachmentType?.uppercase() ?: "FILE"}",
                    style = MaterialTheme.typography.labelMedium
                )
            }
        }
    }
}

@Composable
private fun ActionChipItem(
    chip: ActionChip,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = { Text(chip.label) },
        leadingIcon = if (isSelected) {
            { Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
        } else null
    )
}

@Composable
private fun AttachmentPill(
    fileName: String,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.primaryContainer
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                Icons.Outlined.AttachFile,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                text = fileName,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.weight(1f, fill = false)
            )
            IconButton(onClick = onRemove, modifier = Modifier.size(18.dp)) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "Remove",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}

@Composable
private fun QuickStartSuggestions(onSuggestionClick: (QuickSuggestion) -> Unit) {
    val suggestions = QuickSuggestion.entries.toTypedArray()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Try something",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        suggestions.forEach { suggestion ->
            SuggestionCard(text = suggestion.label, onClick = { onSuggestionClick(suggestion) })
        }
    }
}

enum class QuickSuggestion(val label: String, val input: String, val action: String) {
    REWRITE("📝 Paste an email to rewrite it", "Rewrite this email:\n\n", "text"),
    SUMMARIZE_PDF("📄 Upload a PDF to summarize it", "Summarize this PDF:", "pdf"),
    ANALYZE_IMAGE("🖼️ Analyze an image", "Analyze this image:", "image"),
    TRANSLATE("🌍 Type text to translate it", "Translate this to English: ", "text"),
    RECORD("🎤 Record voice to get notes", "Transcribe this recording:", "audio")
}

@Composable
private fun SuggestionCard(text: String, onClick: () -> Unit) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun RecordingInterface(
    stage: RecordingStage,
    duration: Int,
    onStop: () -> Unit,
    onRedo: () -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        val minutes = (duration / 60).toString().padStart(2, '0')
        val seconds = (duration % 60).toString().padStart(2, '0')

        Text(
            text = "$minutes:$seconds",
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Light,
            color = if (stage == RecordingStage.RECORDING) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
        )

        Text(
            text = if (stage == RecordingStage.RECORDING) "Recording..." else "Recording Complete",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (stage == RecordingStage.REVIEW) {
                // REDO BUTTON
                FilledTonalIconButton(
                    onClick = onRedo,
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(Icons.Outlined.RestartAlt, "Redo", modifier = Modifier.size(28.dp))
                }

                // CANCEL BUTTON
                OutlinedIconButton(
                    onClick = onCancel,
                    modifier = Modifier.size(64.dp)
                ) {
                    Icon(Icons.Outlined.Close, "Cancel", modifier = Modifier.size(28.dp))
                }
            }

            // MAIN ACTION BUTTON (STOP or CONFIRM)
            LargeFloatingActionButton(
                onClick = if (stage == RecordingStage.RECORDING) onStop else onConfirm,
                containerColor = if (stage == RecordingStage.RECORDING) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                contentColor = if (stage == RecordingStage.RECORDING) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
                modifier = Modifier.size(80.dp)
            ) {
                Icon(
                    imageVector = if (stage == RecordingStage.RECORDING) Icons.Outlined.Stop else Icons.Outlined.Check,
                    contentDescription = if (stage == RecordingStage.RECORDING) "Stop" else "Confirm",
                    modifier = Modifier.size(36.dp)
                )
            }
        }
    }
}
