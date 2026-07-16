package com.rhuta.kask.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rhuta.kask.ui.settings.SettingsViewModel

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    
    LaunchedEffect(uiState.allModelsDownloaded) {
        if (uiState.allModelsDownloaded) {
            onComplete()
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.AutoAwesome,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "Welcome to kask",
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            Text(
                text = "Professional on-device AI for text, image, and audio. Everything stays on your phone.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            
            Spacer(modifier = Modifier.height(48.dp))

            if (!uiState.hasEnoughSpace) {
                ErrorMessage(
                    icon = Icons.Outlined.Storage,
                    title = "Storage Low",
                    message = "kask requires ~2GB for AI models. Please free up space."
                )
            } else if (uiState.errorMessage != null) {
                ErrorMessage(
                    icon = Icons.Outlined.Download,
                    title = "Download Error",
                    message = uiState.errorMessage!!
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { viewModel.downloadAllModels() }) {
                    Text("Retry Download")
                }
            } else if (uiState.isDownloading) {
                DownloadInterface(progress = uiState.downloadProgress)
            } else {
                Button(
                    onClick = { viewModel.downloadAllModels() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = MaterialTheme.shapes.large
                ) {
                    Icon(Icons.Outlined.Download, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Download AI Models")
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Size: ~1.2 GB • One-time setup",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
private fun DownloadInterface(progress: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        CircularProgressIndicator(
            progress = { if (progress > 0) progress else 0f },
            modifier = Modifier.size(64.dp),
            strokeWidth = 6.dp,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Text(
            text = if (progress >= 0) "Downloading models: ${(progress * 100).toInt()}%" else "Starting download...",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Text(
            text = "Please keep the app open",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.outline
        )
    }
}

@Composable
private fun ErrorMessage(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, message: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(16.dp))
            Column {
                Text(text = title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                Text(text = message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}
