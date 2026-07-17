package com.rhuta.kask.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.outlined.Note
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rhuta.kask.R
import com.rhuta.kask.data.db.entities.LibraryEntity
import com.rhuta.kask.data.db.entities.contentTypeEnum
import com.rhuta.kask.domain.model.ContentType
import com.rhuta.kask.ui.components.AiniSearchBar
import com.rhuta.kask.ui.components.EmptyState
import com.rhuta.kask.ui.markdown.MarkdownMessage
import com.rhuta.kask.ui.settings.SettingsViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    viewModel: LibraryViewModel = hiltViewModel(),
    settingsViewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
    var itemToRename by remember { mutableStateOf<LibraryEntity?>(null) }
    var itemToDelete by remember { mutableStateOf<LibraryEntity?>(null) }
    var showSortMenu by remember { mutableStateOf(value = false) }

    Scaffold(
        topBar = { 
            TopAppBar(
                title = { Text("Library") },
                actions = {
                    Box {
                        IconButton(onClick = { showSortMenu = true }) {
                            Icon(
                                Icons.AutoMirrored.Filled.Sort,
                                contentDescription = "Sort",
                            )
                        }
                        DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                        ) {
                            LibrarySort.entries.forEach { sortOption ->
                                DropdownMenuItem(
                                    text = { Text(sortOption.label) },
                                    onClick = { 
                                        viewModel.onSortChange(sortOption)
                                        showSortMenu = false 
                                    },
                                    trailingIcon = if (uiState.activeSort == sortOption) {
                                        { Icon(Icons.Outlined.Check, null) }
                                    } else null
                                )
                            }
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
                query = uiState.searchQuery,
                onQueryChange = viewModel::onSearchQueryChange,
                placeholder = "Search keywords…"
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Filter chips
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 8.dp)
            ) {
                items(LibraryFilter.entries) { filter ->
                    FilterChip(
                        selected = uiState.activeFilter == filter,
                        onClick = { viewModel.onFilterChange(filter) },
                        label = { Text(filter.label) },
                        leadingIcon = {
                            Icon(
                                imageVector = filter.icon(),
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    )
                }
            }

            when {
                uiState.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                uiState.items.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EmptyState(
                            icon = {
                                Icon(
                                    imageVector = uiState.activeFilter.icon(),
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            title = "No ${uiState.activeFilter.label.lowercase()} found",
                            subtitle = if (uiState.searchQuery.isNotBlank()) "Try a different search" 
                                      else "Save results to build your library"
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 16.dp)
                    ) {
                        items(uiState.items, key = { it.id }) { item ->
                            LibraryCard(
                                item = item,
                                isDark = settingsState.darkMode,
                                onPin = { viewModel.togglePin(item) },
                                onDelete = { itemToDelete = item },
                                onRename = { itemToRename = item },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    itemToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { itemToDelete = null },
            title = { Text("Delete from library?") },
            text = { Text("Are you sure you want to remove \"${item.title}\"?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteItem(item)
                        itemToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { itemToDelete = null }) { Text("Cancel") }
            }
        )
    }

    // Rename dialog
    itemToRename?.let { item ->
        var newTitle by remember(item.id) { mutableStateOf(item.title) }
        AlertDialog(
            onDismissRequest = { itemToRename = null },
            title = { Text("Rename") },
            text = {
                OutlinedTextField(
                    value = newTitle,
                    onValueChange = { newTitle = it },
                    singleLine = true,
                    label = { Text("Title") }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.renameItem(item, newTitle)
                        itemToRename = null
                    },
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { itemToRename = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun LibraryCard(
    item: LibraryEntity,
    isDark: Boolean,
    onPin: () -> Unit,
    onDelete: () -> Unit,
    onRename: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(value = false) }
    var showMenu by remember { mutableStateOf(value = false) }
    val dateStr = remember(item.savedAt) {
        SimpleDateFormat("MMM d, yyyy • HH:mm", Locale.getDefault()).format(Date(item.savedAt))
    }
    val rotationState by animateFloatAsState(targetValue = if (expanded) 180f else 0f, label = "rotation")

    // Fixed length truncation (24 characters) and prefix cleaning
    val displayTitle = remember(item.title) {
        val cleanTitle = item.title.substringAfter(":").trim()
        if (cleanTitle.length > 24) cleanTitle.take(21) + "..." else cleanTitle
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
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
                Text(
                    text = displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1
                )
                Text(
                    text = dateStr,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Pin Button
            IconButton(onClick = onPin, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = if (item.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    contentDescription = "Pin",
                    tint = if (item.isPinned) MaterialTheme.colorScheme.primary 
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }

            // More (3-dots)
            Box {
                IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.MoreVert, "More", modifier = Modifier.size(18.dp))
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                        onClick = { onRename(); showMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        leadingIcon = {
                            Icon(
                                Icons.Outlined.Delete,
                                null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = { onDelete(); showMenu = false }
                    )
                }
            }

            // Expansion toggle icon (chevron)
            Icon(
                imageVector = Icons.Outlined.ExpandMore,
                contentDescription = "Expand",
                modifier = Modifier
                    .size(24.dp)
                    .rotate(rotationState)
                    .padding(4.dp)
            )
        }

        // EXPANDED CONTENT with Markdown Rendering
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 56.dp, end = 8.dp, top = 8.dp, bottom = 16.dp)
            ) {
                item.textContent?.let { content ->
                    MarkdownMessage(
                        content = content,
                        isDark = isDark,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

private fun LibraryFilter.icon() = when (this) {
    LibraryFilter.ALL -> Icons.Outlined.GridView
    LibraryFilter.PURE_TEXT -> Icons.Outlined.TextFields
    LibraryFilter.IMAGES -> Icons.Outlined.Image
    LibraryFilter.DOCUMENTS -> Icons.Outlined.Description
    LibraryFilter.AUDIO -> Icons.Outlined.AudioFile
}

private fun ContentType.icon() = when (this) {
    ContentType.TEXT  -> Icons.Outlined.TextFields
    ContentType.PDF   -> Icons.Outlined.Description
    ContentType.IMAGE -> Icons.Outlined.Image
    ContentType.AUDIO -> Icons.Outlined.AudioFile
}
