package com.rhuta.kask.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 32.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Spacer(modifier = Modifier.height(48.dp))

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
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
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
                Button(
                    onClick = { viewModel.downloadAllModels() },
                    shape = MaterialTheme.shapes.medium
                ) {
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
                    Spacer(Modifier.width(12.dp))
                    Text("Download AI Models")
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Size: ~1.2 GB • One-time setup",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            TextButton(
                onClick = { viewModel.skipOnboarding() }
            ) {
                Text(
                    if (uiState.isDownloading) "Continue to app while downloading"
                    else "Continue to app without models"
                )
            }
            
            Spacer(modifier = Modifier.height(48.dp))
        }
    }
}

@Composable
private fun DownloadInterface(progress: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        if (progress in 0f..0.98f) {
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(64.dp),
                strokeWidth = 6.dp,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        } else {
            // Indeterminate for "Starting" (<0) or "Verifying" (0.99)
            CircularProgressIndicator(
                modifier = Modifier.size(64.dp),
                strokeWidth = 6.dp,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        val statusText = when {
            progress < 0 -> "Starting download..."
            progress >= 0.99f -> "Verifying integrity..."
            else -> "Downloading models: ${(progress * 100).toInt()}%"
        }

        Text(
            text = statusText,
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
