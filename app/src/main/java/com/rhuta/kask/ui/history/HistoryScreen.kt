package com.rhuta.kask.ui.history

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.material3.SwipeToDismissBoxValue.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rhuta.kask.data.db.entities.HistoryEntity
import com.rhuta.kask.data.db.entities.contentTypeEnum
import com.rhuta.kask.data.db.entities.taskActionEnum
import com.rhuta.kask.domain.model.ContentType
import com.rhuta.kask.domain.model.TaskAction
import com.rhuta.kask.ui.components.AiniSearchBar
import com.rhuta.kask.ui.components.EmptyState
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onContinueChat: (String) -> Unit,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    var showClearDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("History") },
                actions = {
                    if (uiState.items.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(Icons.Outlined.DeleteSweep, contentDescription = "Clear all")
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(4.dp))
            AiniSearchBar(
                query = searchQuery,
                onQueryChange = viewModel::onSearchQueryChange,
                placeholder = "Search history…"
            )
            Spacer(modifier = Modifier.height(8.dp))

            when {
                uiState.isLoading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                uiState.items.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(
                            icon = { Icon(Icons.Outlined.History, contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                            title = if (searchQuery.isBlank()) "No history yet"
                                    else "No results for \"$searchQuery\"",
                            subtitle = if (searchQuery.isBlank())
                                "Your past AI tasks will appear here"
                            else
                                "Try a different search term"
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(
                            items = uiState.items,
                            key = { it.id }
                        ) { item ->
                            SwipeActionsItem(
                                item = item,
                                modifier = Modifier.animateItem(),
                                onDelete = {
                                    viewModel.deleteItem(item)
                                    scope.launch {
                                        val result = snackbarHostState.showSnackbar(
                                            message = "Item deleted",
                                            actionLabel = "Undo",
                                            duration = SnackbarDuration.Short
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            viewModel.undoDelete(item)
                                        }
                                    }
                                },
                                onSave = {
                                    viewModel.saveToLibrary(item)
                                    scope.launch {
                                        snackbarHostState.showSnackbar("Saved to library")
                                    }
                                }
                            ) {
                                HistoryListItem(
                                    item = item,
                                    onClick = { onContinueChat(item.id) }
                                )
                            }
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Clear all history?") },
            text = { Text("This will permanently delete all ${uiState.items.size} items. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = { viewModel.clearAll(); showClearDialog = false },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Clear all") }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeActionsItem(
    item: HistoryEntity,
    onDelete: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                EndToStart -> { onDelete(); true }
                StartToEnd -> { onSave(); true }
                else -> false
            }
        }
    )

    // Reset state when the item is restored or changed
    LaunchedEffect(item.id) {
        if (state.currentValue != Settled) {
            state.snapTo(Settled)
        }
    }

    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        backgroundContent = {
            val direction = state.dismissDirection
            val color = when (direction) {
                EndToStart -> MaterialTheme.colorScheme.errorContainer
                StartToEnd -> MaterialTheme.colorScheme.primaryContainer
                else -> Color.Transparent
            }
            val icon = when (direction) {
                EndToStart -> Icons.Outlined.Delete
                StartToEnd -> Icons.Outlined.BookmarkAdd
                else -> Icons.Outlined.Delete
            }
            val alignment = when (direction) {
                EndToStart -> Alignment.CenterEnd
                StartToEnd -> Alignment.CenterStart
                else -> Alignment.Center
            }
            val padding = when (direction) {
                EndToStart -> Modifier.padding(end = 20.dp)
                StartToEnd -> Modifier.padding(start = 20.dp)
                else -> Modifier
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(color),
                contentAlignment = alignment
            ) {
                if (direction != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = padding,
                        tint = if (direction == EndToStart)
                            MaterialTheme.colorScheme.onErrorContainer
                        else
                            MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }
    ) {
        Surface(color = MaterialTheme.colorScheme.surface) { content() }
    }
}

@Composable
private fun HistoryListItem(item: HistoryEntity, onClick: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val dateStr = remember(item.createdAt) {
        val fmt = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
        fmt.format(Date(item.createdAt))
    }
    val rotationState by animateFloatAsState(targetValue = if (expanded) 180f else 0f)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.contentTypeEnum().icon(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = item.taskActionEnum().label(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.extraSmall
                    ) {
                        Text(
                            text = item.contentTypeEnum().name.lowercase().replaceFirstChar { it.uppercase() },
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                Text(
                    text = item.inputPreview,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = Icons.Default.ExpandMore,
                        contentDescription = "Expand",
                        modifier = Modifier.rotate(rotationState)
                    )
                }
            }
        }

        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 56.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)
            ) {
                Text(
                    text = "Result:",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = item.fullOutput,
                        modifier = Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

// Extension helpers for display
private fun ContentType.icon() = when (this) {
    ContentType.TEXT  -> Icons.Outlined.TextFields
    ContentType.PDF   -> Icons.Outlined.PictureAsPdf
    ContentType.IMAGE -> Icons.Outlined.Image
    ContentType.AUDIO -> Icons.Outlined.Mic
}

private fun TaskAction.label() = when (this) {
    TaskAction.REWRITE       -> "Rewrite"
    TaskAction.SUMMARIZE     -> "Summarize"
    TaskAction.TRANSLATE     -> "Translate"
    TaskAction.EXTRACT       -> "Extract"
    TaskAction.FIX_GRAMMAR   -> "Fix grammar"
    TaskAction.CHANGE_TONE   -> "Change tone"
    TaskAction.TRANSCRIBE    -> "Transcribe"
    TaskAction.OCR           -> "Read text"
    TaskAction.DESCRIBE      -> "Describe"
    TaskAction.ASK_QUESTION  -> "Ask a question"
    TaskAction.FREE_FORM     -> "Generate"
}
