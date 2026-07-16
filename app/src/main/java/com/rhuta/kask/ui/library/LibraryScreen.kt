package com.rhuta.kask.ui.library

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rhuta.kask.data.db.entities.LibraryEntity
import com.rhuta.kask.data.db.entities.contentTypeEnum
import com.rhuta.kask.domain.model.ContentType
import com.rhuta.kask.ui.components.AiniSearchBar
import com.rhuta.kask.ui.components.EmptyState
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(viewModel: LibraryViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
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

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            // ROW 1: TITLE + BUTTONS
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = item.contentTypeEnum().icon(),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                
                // Pin Button
                IconButton(onClick = onPin, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = if (item.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                        contentDescription = "Pin",
                        tint = if (item.isPinned) MaterialTheme.colorScheme.primary 
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // More (3-dots)
                Box {
                    IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.MoreVert, "More", modifier = Modifier.size(20.dp))
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

                // Expansion toggle
                IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Outlined.ExpandMore,
                        contentDescription = "Expand",
                        modifier = Modifier
                            .size(22.dp)
                            .rotate(rotationState)
                    )
                }
            }

            // ROW 2: TIMESTAMP
            Text(
                text = dateStr,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 28.dp) // Align with text after icon
            )

            // OPTIONAL: EXPANDED CONTENT
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    item.textContent?.let { content ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = content,
                                modifier = Modifier.padding(12.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun LibraryFilter.icon() = when (this) {
    LibraryFilter.ALL -> Icons.Outlined.GridView
    LibraryFilter.PURE_TEXT -> Icons.AutoMirrored.Outlined.Note
    LibraryFilter.IMAGES -> Icons.Outlined.Image
    LibraryFilter.DOCUMENTS -> Icons.Outlined.Description
    LibraryFilter.AUDIO -> Icons.Outlined.AudioFile
}

private fun ContentType.icon() = when (this) {
    ContentType.TEXT  -> Icons.AutoMirrored.Outlined.Note
    ContentType.PDF   -> Icons.Outlined.Description
    ContentType.IMAGE -> Icons.Outlined.Image
    ContentType.AUDIO -> Icons.Outlined.AudioFile
}
