package com.rhuta.kask.ui.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rhuta.kask.data.network.DownloadStatus
import com.rhuta.kask.data.repository.KaskRepository
import com.rhuta.kask.domain.model.EngineTier
import com.rhuta.kask.domain.model.ModelManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// DataStore extension
val Context.settingsDataStore by preferencesDataStore("kask_settings")

object PrefKeys {
    val APP_LANGUAGE      = stringPreferencesKey("app_language")
    val CHAT_FONT_SIZE    = floatPreferencesKey("chat_font_size")
    val SAVE_HISTORY      = booleanPreferencesKey("save_history")
    val ANALYTICS_OPT_IN  = booleanPreferencesKey("analytics_opt_in")
    val DARK_MODE         = booleanPreferencesKey("dark_mode")
    val IS_PRO_USER       = booleanPreferencesKey("is_pro_user")
    val PREFERRED_MODEL   = stringPreferencesKey("preferred_model") // "auto", EngineTier.name
}

data class SettingsUiState(
    val appLanguage: String = "en",
    val chatFontSize: Float = 14f,
    val saveHistory: Boolean = true,
    val modelPath: String = "",
    val modelDownloaded: Boolean = false,
    val allModelsDownloaded: Boolean = false,
    val whisperDownloaded: Boolean = false,
    val analyticsOptIn: Boolean = false,
    val darkMode: Boolean = false,
    val isProUser: Boolean = false,
    val preferredModel: String = "auto",
    val appVersion: String = "1.0.0",
    val downloadProgress: Float = -1f,
    val isDownloading: Boolean = false,
    val errorMessage: String? = null,
    val hasEnoughSpace: Boolean = true,
    val currentHardwareTier: EngineTier = EngineTier.EFFICIENT
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val modelManager: ModelManager,
    private val repository: KaskRepository,
) : ViewModel() {

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)

    val uiState: StateFlow<SettingsUiState> = combine(
        context.settingsDataStore.data,
        _downloadState
    ) { prefs, downloadState ->
        val isPro = prefs[PrefKeys.IS_PRO_USER] ?: false
        val preferredName = prefs[PrefKeys.PREFERRED_MODEL] ?: "auto"
        val resolvedTier = modelManager.getResolvedTier(isPro, preferredName)
        
        SettingsUiState(
            appLanguage      = prefs[PrefKeys.APP_LANGUAGE] ?: "en",
            chatFontSize     = prefs[PrefKeys.CHAT_FONT_SIZE] ?: 14f,
            saveHistory      = prefs[PrefKeys.SAVE_HISTORY]     ?: true,
            modelPath        = modelManager.getRecommendedModelFile(resolvedTier).absolutePath,
            modelDownloaded  = modelManager.isTextModelDownloaded(),
            allModelsDownloaded = modelManager.isEverythingReady(),
            whisperDownloaded = modelManager.isWhisperDownloaded(),
            analyticsOptIn   = prefs[PrefKeys.ANALYTICS_OPT_IN] ?: false,
            darkMode         = prefs[PrefKeys.DARK_MODE]        ?: false,
            isProUser        = isPro,
            preferredModel   = preferredName,
            downloadProgress = if (downloadState is DownloadState.Progress) downloadState.progress else -1f,
            isDownloading    = (downloadState is DownloadState.Progress) || (downloadState is DownloadState.Started),
            errorMessage     = if (downloadState is DownloadState.Error) downloadState.message else null,
            hasEnoughSpace   = modelManager.hasEnoughSpaceForDownload(),
            currentHardwareTier = modelManager.getCurrentTier()
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState()
    )

    fun setAppLanguage(lang: String)      = save { it[PrefKeys.APP_LANGUAGE] = lang }
    fun setChatFontSize(size: Float)      = save { it[PrefKeys.CHAT_FONT_SIZE] = size }
    fun setSaveHistory(enabled: Boolean) = save { it[PrefKeys.SAVE_HISTORY] = enabled }
    fun setAnalyticsOptIn(opt: Boolean)  = save { it[PrefKeys.ANALYTICS_OPT_IN] = opt }
    fun setDarkMode(enabled: Boolean)    = save { it[PrefKeys.DARK_MODE] = enabled }
    
    fun setPreferredModel(modelName: String) {
        save { it[PrefKeys.PREFERRED_MODEL] = modelName }
        
        viewModelScope.launch {
            val isPro = context.settingsDataStore.data.first()[PrefKeys.IS_PRO_USER] ?: false
            val resolvedTier = modelManager.getResolvedTier(isPro, modelName)
            if (!modelManager.isTierReady(resolvedTier)) {
                downloadTier(resolvedTier)
            }
        }
    }
    
    fun toggleProStatus() = save { it[PrefKeys.IS_PRO_USER] = !(it[PrefKeys.IS_PRO_USER] ?: false) }

    fun clearHistory() {
        viewModelScope.launch { repository.clearHistory() }
    }

    fun clearLibrary() {
        viewModelScope.launch { repository.clearLibrary() }
    }

    fun downloadAllModels() {
        if (!modelManager.hasEnoughSpaceForDownload(EngineTier.EFFICIENT)) {
            val required = EngineTier.EFFICIENT.requiredSpaceBytes / (1024 * 1024 * 1024.0)
            _downloadState.value = DownloadState.Error("Not enough storage space (need ${"%.1f".format(required)}GB free)")
            return
        }
        viewModelScope.launch {
            modelManager.downloadAllModels().collect { status ->
                updateDownloadState(status)
            }
        }
    }

    private fun downloadTier(tier: EngineTier) {
        viewModelScope.launch {
            modelManager.downloadTier(tier).collect { status ->
                updateDownloadState(status)
            }
        }
    }

    private fun updateDownloadState(status: DownloadStatus) {
        _downloadState.value = when (status) {
            is DownloadStatus.Started -> DownloadState.Started
            is DownloadStatus.Progress -> DownloadState.Progress(status.progress)
            is DownloadStatus.Finished -> DownloadState.Idle
            is DownloadStatus.Error -> DownloadState.Error(status.message)
        }
    }

    private fun save(block: suspend (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        viewModelScope.launch { context.settingsDataStore.edit { block(it) } }
    }
}

sealed class DownloadState {
    object Idle : DownloadState()
    object Started : DownloadState()
    data class Progress(val progress: Float) : DownloadState()
    data class Error(val message: String) : DownloadState()
}
