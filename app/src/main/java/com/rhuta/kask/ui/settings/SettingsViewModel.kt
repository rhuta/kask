package com.rhuta.kask.ui.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rhuta.kask.BuildConfig
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
    val HAS_SKIPPED_ONBOARDING = booleanPreferencesKey("has_skipped_onboarding")
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
    val hasSkippedOnboarding: Boolean = false,
    val isInitialized: Boolean = false,
    val preferredModel: String = "auto",
    val appVersion: String = BuildConfig.VERSION_NAME,
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

    val uiState: StateFlow<SettingsUiState> = combine(
        context.settingsDataStore.data,
        modelManager.globalDownloadStatus
    ) { prefs, downloadStatus ->
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
            hasSkippedOnboarding = prefs[PrefKeys.HAS_SKIPPED_ONBOARDING] ?: false,
            isInitialized    = true,
            preferredModel   = preferredName,
            downloadProgress = if (downloadStatus is DownloadStatus.Progress) downloadStatus.progress else -1f,
            isDownloading    = (downloadStatus is DownloadStatus.Progress) || (downloadStatus is DownloadStatus.Started),
            errorMessage     = if (downloadStatus is DownloadStatus.Error) downloadStatus.message else null,
            hasEnoughSpace   = modelManager.hasEnoughSpaceForDownload(),
            currentHardwareTier = modelManager.getCurrentTier(),
            appVersion       = BuildConfig.VERSION_NAME
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
                modelManager.startTierDownload(resolvedTier)
            }
        }
    }
    
    fun toggleProStatus() = save { it[PrefKeys.IS_PRO_USER] = !(it[PrefKeys.IS_PRO_USER] ?: false) }

    fun skipOnboarding() = save { it[PrefKeys.HAS_SKIPPED_ONBOARDING] = true }

    fun clearHistory() {
        viewModelScope.launch { repository.clearHistory() }
    }

    fun clearLibrary() {
        viewModelScope.launch { repository.clearLibrary() }
    }

    fun downloadAllModels() {
        if (uiState.value.isDownloading) return

        if (!modelManager.hasEnoughSpaceForDownload(EngineTier.EFFICIENT)) {
            return
        }
        modelManager.startBackgroundDownload()
    }

    private fun downloadTier(tier: EngineTier) {
        modelManager.startTierDownload(tier)
    }

    private fun save(block: suspend (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        viewModelScope.launch { context.settingsDataStore.edit { block(it) } }
    }
}
