package com.rhuta.kask.domain.model

import android.app.ActivityManager
import android.content.Context
import android.os.StatFs
import com.rhuta.kask.data.network.DownloadManager
import com.rhuta.kask.data.network.DownloadStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt

/**
 * SYSTEMATIC ENGINE DEFINITION
 */
enum class EngineTier(
    val displayName: String,
    val minRamGb: Int,
    val requiredSpaceBytes: Long
) {
    EFFICIENT("Efficient Engine", 0, 5 * 1024 * 1024 * 1024L),
    BALANCED("Balanced Engine", 8, 5 * 1024 * 1024 * 1024L),
    PRECISION("Precision Engine", 12, 8 * 1024 * 1024 * 1024L)
}

@Singleton
class ModelManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val downloadManager: DownloadManager,
) {
    val modelsDir = File(context.filesDir, "models")

    // CACHED STATUS FLAGS
    private var _cachedIsHighRam: Boolean? = null
    private var _cachedIsUltraHighRam: Boolean? = null

    init {
        if (!modelsDir.exists()) modelsDir.mkdirs()
    }

    // --- BASE FILES (ASR) ---
    val asrModelFile = File(modelsDir, "Qwen3-ASR-0.6B-Q4KM.gguf")
    val asrProjectorFile = File(modelsDir, "mmproj-Qwen3-ASR-0.6B-Q80.gguf")

    // --- LLM FILES (Multimodal: Text + Vision) ---
    val llm08ModelFile = File(modelsDir, "Qwen3.5-0.8B-Q4KM.gguf")
    val llm08ProjectorFile = File(modelsDir, "mmproj-Qwen3.5-0.8B-Q80.gguf")

    val llm2bModelFile = File(modelsDir, "Qwen3.5-2B-Q4KM.gguf")
    val llm2bProjectorFile = File(modelsDir, "mmproj-Qwen3.5-2B-Q80.gguf")

    val llm4bModelFile = File(modelsDir, "Qwen3.5-4B-Q4KM.gguf")
    val llm4bProjectorFile = File(modelsDir, "mmproj-Qwen3.5-4B-Q80.gguf")

    // --- HUGGINGFACE ENDPOINTS ---
    private val BASE_URL = "https://huggingface.co/rhuta/Qwen/resolve/main"

    // --- HARDWARE RESOLVERS ---

    fun isHighRamDevice(): Boolean {
        _cachedIsHighRam?.let { return it }
        val mem = getTotalRamGb()
        val result = mem >= EngineTier.BALANCED.minRamGb
        _cachedIsHighRam = result
        return result
    }

    fun isUltraHighRamDevice(): Boolean {
        _cachedIsUltraHighRam?.let { return it }
        val mem = getTotalRamGb()
        val result = mem >= EngineTier.PRECISION.minRamGb
        _cachedIsUltraHighRam = result
        return result
    }

    fun getCurrentTier(): EngineTier {
        return when {
            isUltraHighRamDevice() -> EngineTier.PRECISION
            isHighRamDevice() -> EngineTier.BALANCED
            else -> EngineTier.EFFICIENT
        }
    }

    fun getTotalRamGb(): Int {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        val decimalDivisor = 1_000_000_000.0
        return (memoryInfo.totalMem / decimalDivisor).roundToInt()
    }

    fun hasEnoughSpaceForDownload(): Boolean {
        val stat = StatFs(context.filesDir.path)
        val availableBytes = stat.availableBlocksLong * stat.blockSizeLong
        return availableBytes >= getCurrentTier().requiredSpaceBytes
    }

    /**
     * Resolves the effective engine tier based on pro status and user preference.
     */
    fun getResolvedTier(isPro: Boolean, preferredModelName: String?): EngineTier {
        val hardwareTier = getCurrentTier()
        if (!isPro || preferredModelName == null || preferredModelName == "auto") {
            return hardwareTier
        }
        val preferredTier = EngineTier.entries.find { it.name == preferredModelName } ?: hardwareTier
        return if (preferredTier.ordinal <= hardwareTier.ordinal) preferredTier else hardwareTier
    }

    private fun isValidFile(file: File): Boolean = file.exists() && file.length() >= 10 * 1024 * 1024

    // --- READINESS ---

    fun getRequiredFilesForTier(tier: EngineTier): List<Pair<String, File>> {
        val list = mutableListOf<Pair<String, File>>()
        list.add("$BASE_URL/Qwen3-ASR-0.6B-Q4KM.gguf" to asrModelFile)
        list.add("$BASE_URL/mmproj-Qwen3-ASR-0.6B-Q80.gguf" to asrProjectorFile)

        when(tier) {
            EngineTier.PRECISION -> {
                list.add("$BASE_URL/Qwen3.5-4B-Q4KM.gguf" to llm4bModelFile)
                list.add("$BASE_URL/mmproj-Qwen3.5-4B-Q80.gguf" to llm4bProjectorFile)
            }
            EngineTier.BALANCED -> {
                list.add("$BASE_URL/Qwen3.5-2B-Q4KM.gguf" to llm2bModelFile)
                list.add("$BASE_URL/mmproj-Qwen3.5-2B-Q80.gguf" to llm2bProjectorFile)
            }
            EngineTier.EFFICIENT -> {
                list.add("$BASE_URL/Qwen3.5-0.8B-Q4KM.gguf" to llm08ModelFile)
                list.add("$BASE_URL/mmproj-Qwen3.5-0.8B-Q80.gguf" to llm08ProjectorFile)
            }
        }
        return list
    }

    fun isEverythingReady(): Boolean {
        val asrReady = isValidFile(asrModelFile) && isValidFile(asrProjectorFile)
        val llmReady = isValidFile(llm4bModelFile) || isValidFile(llm2bModelFile) || isValidFile(llm08ModelFile)
        val projectorReady = isValidFile(llm4bProjectorFile) || isValidFile(llm2bProjectorFile) || isValidFile(llm08ProjectorFile)
        return asrReady && llmReady && projectorReady
    }

    fun isTierReady(tier: EngineTier): Boolean {
        return getRequiredFilesForTier(tier).all { isValidFile(it.second) }
    }

    fun isWhisperDownloaded(): Boolean = isValidFile(asrModelFile)
    fun isAsrProjectorDownloaded(): Boolean = isValidFile(asrProjectorFile)
    
    fun isTextModelDownloaded(preferredTier: EngineTier? = null): Boolean {
        val file = getRecommendedModelFile(preferredTier)
        return isValidFile(file)
    }

    /**
     * Sequentially downloads all models needed for the current hardware tier.
     */
    fun downloadAllModels(): Flow<DownloadStatus> = flow {
        val models = getRequiredFilesForTier(getCurrentTier())
        var completed = 0
        for ((url, dest) in models) {
            if (dest.exists()) {
                completed++
                continue
            }
            downloadManager.downloadFile(url, dest).collect { status ->
                when (status) {
                    is DownloadStatus.Progress -> {
                        val overallProgress = (completed.toFloat() + status.progress) / models.size
                        emit(DownloadStatus.Progress(overallProgress))
                    }
                    is DownloadStatus.Error -> emit(status)
                    is DownloadStatus.Finished -> {
                        completed++
                        emit(DownloadStatus.Progress(completed.toFloat() / models.size))
                    }
                    DownloadStatus.Started -> emit(DownloadStatus.Started)
                }
            }
        }
        emit(DownloadStatus.Finished(llm08ModelFile))
    }

    fun downloadTier(tier: EngineTier): Flow<DownloadStatus> = flow {
        val models = getRequiredFilesForTier(tier)
        var completed = 0
        for ((url, dest) in models) {
            if (dest.exists()) {
                completed++
                continue
            }
            downloadManager.downloadFile(url, dest).collect { status ->
                when (status) {
                    is DownloadStatus.Progress -> {
                        val overallProgress = (completed.toFloat() + status.progress) / models.size
                        emit(DownloadStatus.Progress(overallProgress))
                    }
                    is DownloadStatus.Error -> emit(status)
                    is DownloadStatus.Finished -> {
                        completed++
                        emit(DownloadStatus.Progress(completed.toFloat() / models.size))
                    }
                    DownloadStatus.Started -> emit(DownloadStatus.Started)
                }
            }
        }
        emit(DownloadStatus.Finished(models.last().second))
    }

    // --- ENGINE ACCESSORS ---

    fun getRecommendedModelFile(preferredTier: EngineTier? = null): File {
        preferredTier?.let {
            val file = when(it) {
                EngineTier.PRECISION -> llm4bModelFile
                EngineTier.BALANCED -> llm2bModelFile
                EngineTier.EFFICIENT -> llm08ModelFile
            }
            if (isValidFile(file)) return file
        }
        if (isValidFile(llm4bModelFile)) return llm4bModelFile
        if (isValidFile(llm2bModelFile)) return llm2bModelFile
        if (isValidFile(llm08ModelFile)) return llm08ModelFile
        
        return when (getCurrentTier()) {
            EngineTier.PRECISION -> llm4bModelFile
            EngineTier.BALANCED -> llm2bModelFile
            EngineTier.EFFICIENT -> llm08ModelFile
        }
    }

    fun getRecommendedVisionFile(preferredTier: EngineTier? = null): File {
        preferredTier?.let {
            val file = when(it) {
                EngineTier.PRECISION -> llm4bProjectorFile
                EngineTier.BALANCED -> llm2bProjectorFile
                EngineTier.EFFICIENT -> llm08ProjectorFile
            }
            if (isValidFile(file)) return file
        }
        if (isValidFile(llm4bProjectorFile)) return llm4bProjectorFile
        if (isValidFile(llm2bProjectorFile)) return llm2bProjectorFile
        if (isValidFile(llm08ProjectorFile)) return llm08ProjectorFile
        
        return when (getCurrentTier()) {
            EngineTier.PRECISION -> llm4bProjectorFile
            EngineTier.BALANCED -> llm2bProjectorFile
            EngineTier.EFFICIENT -> llm08ProjectorFile
        }
    }
}
