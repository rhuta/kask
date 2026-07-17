package com.rhuta.kask.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rhuta.kask.R
import com.rhuta.kask.domain.model.EngineTier

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showAppLanguagePicker by remember { mutableStateOf(value = false) }
    var showFontSizePicker by remember { mutableStateOf(value = false) }
    var showModelPicker by remember { mutableStateOf(value = false) }
    var showClearHistoryDialog by remember { mutableStateOf(value = false) }
    var showClearLibraryDialog by remember { mutableStateOf(value = false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            // ---- Account / Subscription Section ------------------------
            SettingsSectionHeader("Account")
            ListItem(
                headlineContent = { Text(if (state.isProUser) "Kask Pro User" else "Free Account") },
                supportingContent = { Text(if (state.isProUser) "Active Subscription" else "Upgrade to unlock all models") },
                leadingContent = {
                    Icon(
                        if (state.isProUser) Icons.Outlined.WorkspacePremium else Icons.Outlined.AccountCircle,
                        contentDescription = null,
                        tint = if (state.isProUser) Color(0xFFFFD700) else MaterialTheme.colorScheme.primary
                    )
                },
                trailingContent = {
                    Button(onClick = { viewModel.toggleProStatus() }) {
                        Text(if (state.isProUser) "Manage" else "Upgrade")
                    }
                }
            )

            // ---- Model management section -------------------------------
            SettingsSectionHeader(stringResource(R.string.ai_model))

            SettingsListItem(
                icon = Icons.Outlined.Analytics,
                title = "Preferred Model",
                subtitle = when (state.preferredModel) {
                    EngineTier.EFFICIENT.name -> "Fast (0.8B)"
                    EngineTier.BALANCED.name -> "Balanced (2B)"
                    EngineTier.PRECISION.name -> "Quality (4B)"
                    else -> "Auto-detect (${state.currentHardwareTier.displayName.substringBefore(" ")})"
                },
                onClick = { if (state.isProUser) showModelPicker = true },
                enabled = state.isProUser,
                badge = if (!state.isProUser) "PRO" else null
            )

            ModelStatusCard(
                title = "AI Engine Status",
                downloaded = state.allModelsDownloaded,
                description = if (state.allModelsDownloaded) {
                    val tier = when {
                        state.modelPath.contains("4B") -> "4B"
                        state.modelPath.contains("2B") -> "2B"
                        else -> "0.8B"
                    }
                    "Active Tier: $tier • All models ready for offline use"
                } else {
                    "Download models to enable offline text, image, and audio AI."
                },
                isDownloading = state.isDownloading,
                progress = state.downloadProgress,
                onDownload = viewModel::downloadAllModels
            )

            if (!state.hasEnoughSpace) {
                Card(
                    modifier = Modifier.padding(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Storage, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "Low storage space. You need at least 4GB free.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            if (state.errorMessage != null) {
                Text(
                    text = state.errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }

            // ---- Appearance section -------------------------------------
            SettingsSectionHeader(stringResource(R.string.appearance))

            SettingsSwitchItem(
                icon = Icons.Outlined.DarkMode,
                title = stringResource(R.string.dark_mode),
                subtitle = "Force dark theme",
                checked = state.darkMode,
                onCheckedChange = viewModel::setDarkMode
            )

            SettingsListItem(
                icon = Icons.Outlined.FormatSize,
                title = stringResource(R.string.chat_font_size),
                subtitle = "${state.chatFontSize.toInt()} sp",
                onClick = { showFontSizePicker = true }
            )

            SettingsListItem(
                icon = Icons.Outlined.Language,
                title = stringResource(R.string.app_language),
                subtitle = languageLabel(state.appLanguage),
                onClick = { showAppLanguagePicker = true }
            )

            // ---- Privacy section ----------------------------------------
            SettingsSectionHeader(stringResource(R.string.privacy))

            SettingsSwitchItem(
                icon = Icons.Outlined.History,
                title = stringResource(R.string.save_history),
                subtitle = "Store past results on this device",
                checked = state.saveHistory,
                onCheckedChange = viewModel::setSaveHistory
            )

            // ---- Storage section ----------------------------------------
            SettingsSectionHeader(stringResource(R.string.storage))

            SettingsListItem(
                icon = Icons.Outlined.FolderOpen,
                title = stringResource(R.string.clear_history),
                subtitle = "Delete all past AI results",
                onClick = { showClearHistoryDialog = true }
            )

            SettingsListItem(
                icon = Icons.Outlined.DeleteSweep,
                title = stringResource(R.string.clear_library),
                subtitle = "Remove all saved items",
                onClick = { showClearLibraryDialog = true }
            )

            // ---- About section ------------------------------------------
            SettingsSectionHeader(stringResource(R.string.about))

            ListItem(
                headlineContent = { Text("Version") },
                trailingContent = {
                    Text(
                        text = state.appVersion,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                leadingContent = {
                    Icon(
                        Icons.Outlined.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )

            ListItem(
                headlineContent = { Text("Copyright") },
                supportingContent = { Text("copyright (c) rhuta 2026") },
                leadingContent = {
                    Icon(
                        Icons.Outlined.Copyright,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )

            ListItem(
                headlineContent = { Text("Contact") },
                supportingContent = { Text("rhuta@msn.com") },
                leadingContent = {
                    Icon(
                        Icons.Outlined.Email,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            )

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Model Picker (PRO only)
    if (showModelPicker) {
        val models = listOf(
            "auto" to "Auto-detect",
            EngineTier.EFFICIENT.name to "Fast (0.8B)",
            EngineTier.BALANCED.name to "Balanced (2B)",
            EngineTier.PRECISION.name to "Quality (4B)"
        )
        AlertDialog(
            onDismissRequest = { showModelPicker = false },
            title = { Text("Select AI Model") },
            text = {
                Column {
                    models.forEach { (code, label) ->
                        RadioListItem(
                            label = label,
                            selected = state.preferredModel == code,
                            onClick = {
                                viewModel.setPreferredModel(code)
                                showModelPicker = false
                            }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showModelPicker = false }) { Text("Cancel") }
            }
        )
    }

    // Language picker
    if (showAppLanguagePicker) {
        val languages = listOf("en" to "English", "zh" to "Chinese", "es" to "Spanish", "hi" to "Hindi")
        AlertDialog(
            onDismissRequest = { showAppLanguagePicker = false },
            title = { Text(stringResource(R.string.app_language)) },
            text = {
                Column {
                    languages.forEach { (code, name) ->
                        RadioListItem(
                            label = name,
                            selected = state.appLanguage == code,
                            onClick = { viewModel.setAppLanguage(code); showAppLanguagePicker = false }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAppLanguagePicker = false }) { Text("Cancel") }
            }
        )
    }

    // Font Size picker
    if (showFontSizePicker) {
        val sizes = listOf(12f to "Small", 14f to "Normal", 16f to "Large", 18f to "Extra Large")
        AlertDialog(
            onDismissRequest = { showFontSizePicker = false },
            title = { Text("Chat Font Size") },
            text = {
                Column {
                    sizes.forEach { (size, label) ->
                        RadioListItem(
                            label = label,
                            selected = state.chatFontSize == size,
                            onClick = { viewModel.setChatFontSize(size); showFontSizePicker = false }
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showFontSizePicker = false }) { Text("Cancel") }
            }
        )
    }

    // Clear History Dialog
    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("Clear all history?") },
            text = { Text("This will delete all past conversations. This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = { 
                        viewModel.clearHistory()
                        showClearHistoryDialog = false 
                    }
                ) {
                    Text("Clear", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Clear Library Dialog
    if (showClearLibraryDialog) {
        AlertDialog(
            onDismissRequest = { showClearLibraryDialog = false },
            title = { Text("Clear your library?") },
            text = { Text("This will permanently remove all items you've saved to your library.") },
            confirmButton = {
                TextButton(
                    onClick = { 
                        viewModel.clearLibrary()
                        showClearLibraryDialog = false 
                    }
                ) {
                    Text("Clear All", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearLibraryDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp)
    )
}

@Composable
private fun ModelStatusCard(
    title: String,
    downloaded: Boolean,
    description: String,
    isDownloading: Boolean,
    progress: Float,
    onDownload: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (downloaded)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = if (downloaded) Icons.Outlined.CheckCircle else Icons.Outlined.CloudDownload,
                    contentDescription = null,
                    tint = if (downloaded) MaterialTheme.colorScheme.onPrimaryContainer
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = if (downloaded) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (downloaded) MaterialTheme.colorScheme.onPrimaryContainer
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!downloaded && !isDownloading) {
                    FilledTonalButton(onClick = onDownload) {
                        Text("Download")
                    }
                }
            }
            if (isDownloading && (progress >= 0)) {
                Spacer(modifier = Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun SettingsListItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    badge: String? = null
) {
    ListItem(
        headlineContent = { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f))
                if (badge != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.tertiary,
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = badge,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        supportingContent = {
            Text(subtitle, color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f))
        },
        leadingContent = {
            Icon(
                icon,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            )
        },
        trailingContent = {
            Icon(
                Icons.Outlined.ChevronRight,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            )
        },
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier)
    )
    HorizontalDivider(
        modifier = Modifier.padding(start = 56.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    )
}

@Composable
private fun SettingsSwitchItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = {
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        },
        leadingContent = {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    )
    HorizontalDivider(
        modifier = Modifier.padding(start = 56.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    )
}

@Composable
private fun RadioListItem(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, modifier = Modifier.padding(start = 8.dp))
    }
}

private fun languageLabel(code: String) = mapOf(
    "en" to "English", "zh" to "Chinese", "es" to "Spanish", "hi" to "Hindi"
)[code] ?: code
