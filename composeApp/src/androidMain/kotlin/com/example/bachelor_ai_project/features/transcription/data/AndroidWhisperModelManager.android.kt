package com.example.bachelor_ai_project.features.transcription.data

import android.content.Context
import com.example.bachelor_ai_project.features.transcription.domain.OnDeviceWhisperModelState
import com.example.bachelor_ai_project.features.transcription.domain.WhisperLocalModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

/**
 * Verwaltet Auswahl, Persistenz und Download des Android-Whisper-Modells.
 */
class AndroidWhisperModelManager(
    private val context: Context,
    private val baseModelPath: String,
    private val smallModelDownloadUrl: String,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val modelsDir = File(context.filesDir, MODELS_DIR_NAME)
    private val bundledBaseModelFile = File(modelsDir, BASE_MODEL_FILE_NAME)
    private val smallModelFile = File(modelsDir, SMALL_MODEL_FILE_NAME)

    private val _state = MutableStateFlow(buildState())
    val state: StateFlow<OnDeviceWhisperModelState> = _state.asStateFlow()

    fun resolveActiveModelPath(): String {
        val current = state.value
        val selectedFile = when (current.selectedModel) {
            WhisperLocalModel.BASE -> resolveBaseModelFile()
            WhisperLocalModel.SMALL -> smallModelFile
        }

        require(selectedFile.exists() && selectedFile.isFile && selectedFile.canRead()) {
            when (current.selectedModel) {
                WhisperLocalModel.BASE -> "Whisper Base Modell nicht lesbar: $baseModelPath"
                WhisperLocalModel.SMALL ->
                    "Whisper Small Modell nicht gefunden. Bitte zuerst herunterladen."
            }
        }

        return selectedFile.absolutePath
    }

    private fun resolveBaseModelFile(): File {
        val configuredBase = File(baseModelPath)
        if (configuredBase.exists() && configuredBase.isFile && configuredBase.canRead()) {
            return configuredBase
        }

        if (bundledBaseModelFile.exists() && bundledBaseModelFile.isFile && bundledBaseModelFile.canRead()) {
            return bundledBaseModelFile
        }

        if (hasBundledBaseModelAsset()) {
            modelsDir.mkdirs()
            copyAssetToFile(
                assetPath = ASSET_BASE_MODEL_PATH,
                targetFile = bundledBaseModelFile,
            )
            return bundledBaseModelFile
        }

        return configuredBase
    }

    fun selectModel(model: WhisperLocalModel) {
        if (model == WhisperLocalModel.SMALL && !smallModelFile.exists()) {
            _state.value = buildState(lastError = "Whisper Small ist noch nicht heruntergeladen.")
            return
        }

        prefs.edit().putString(KEY_SELECTED_MODEL, model.name).apply()
        _state.value = buildState(statusMessage = "Aktives Modell: ${modelLabel(model)}")
    }

    suspend fun prepareSmallModel() {
        if (_state.value.isDownloadingSmallModel) return
        if (smallModelFile.exists()) {
            _state.value = buildState(statusMessage = "Whisper Small ist bereits vorhanden.")
            return
        }

        val hasBundledAsset = hasBundledSmallModelAsset()
        val canDownload = smallModelDownloadUrl.isNotBlank()
        if (!hasBundledAsset && !canDownload) {
            _state.value = buildState(
                lastError = "Whisper Small weder im App-Bundle vorhanden noch per URL konfiguriert."
            )
            return
        }

        _state.value = buildState(
            isDownloading = true,
            progressPercent = 0,
            statusMessage = if (hasBundledAsset) "Installation aus App-Bundle gestartet..." else "Download gestartet...",
        )

        val tmpFile = File(modelsDir, "$SMALL_MODEL_FILE_NAME.part")

        try {
            withContext(Dispatchers.IO) {
                modelsDir.mkdirs()
                if (hasBundledAsset) {
                    copyBundledAssetToFile(tmpFile)
                    _state.value = buildState(
                        isDownloading = true,
                        progressPercent = null,
                        statusMessage = "Installation aus App-Bundle laeuft...",
                    )
                } else {
                    downloadToFile(
                        url = smallModelDownloadUrl,
                        targetFile = tmpFile,
                        onProgress = { progress ->
                            _state.value = buildState(
                                isDownloading = true,
                                progressPercent = progress,
                                statusMessage = if (progress == null) {
                                    "Download laeuft..."
                                } else {
                                    "Download laeuft: $progress%"
                                },
                            )
                        },
                    )
                }

                if (!tmpFile.renameTo(smallModelFile)) {
                    throw IOException("Temporare Modelldatei konnte nicht gespeichert werden.")
                }
            }

            _state.value = buildState(
                statusMessage = if (hasBundledAsset) {
                    "Whisper Small aus App-Bundle installiert. Du kannst jetzt auf Small wechseln."
                } else {
                    "Whisper Small heruntergeladen. Du kannst jetzt auf Small wechseln."
                },
            )
        } catch (e: Exception) {
            tmpFile.delete()
            _state.value = buildState(lastError = "Bereitstellung fehlgeschlagen: ${e.message ?: "Unbekannter Fehler"}")
        }
    }

    private fun buildState(
        isDownloading: Boolean = false,
        progressPercent: Int? = null,
        statusMessage: String? = null,
        lastError: String? = null,
    ): OnDeviceWhisperModelState {
        val selected = parsePersistedSelection()
        return OnDeviceWhisperModelState(
            supportsModelManagement = hasReadableBaseModel() || hasBundledBaseModelAsset(),
            selectedModel = selected,
            isSmallModelDownloaded = smallModelFile.exists(),
            canInstallSmallModelFromBundle = hasBundledSmallModelAsset(),
            canDownloadSmallModel = smallModelDownloadUrl.isNotBlank(),
            isDownloadingSmallModel = isDownloading,
            smallModelDownloadProgressPercent = progressPercent,
            smallModelStatusMessage = statusMessage,
            lastError = lastError,
        )
    }

    private fun parsePersistedSelection(): WhisperLocalModel {
        val raw = prefs.getString(KEY_SELECTED_MODEL, WhisperLocalModel.BASE.name)
        val saved = runCatching { WhisperLocalModel.valueOf(raw.orEmpty()) }.getOrDefault(WhisperLocalModel.BASE)
        return if (saved == WhisperLocalModel.SMALL && !smallModelFile.exists()) {
            WhisperLocalModel.BASE
        } else {
            saved
        }
    }

    private fun hasReadableBaseModel(): Boolean {
        val file = File(baseModelPath)
        return file.exists() && file.isFile && file.canRead()
    }

    private fun modelLabel(model: WhisperLocalModel): String = when (model) {
        WhisperLocalModel.BASE -> "Base"
        WhisperLocalModel.SMALL -> "Small"
    }

    private fun hasBundledSmallModelAsset(): Boolean = runCatching {
        context.assets.open(ASSET_SMALL_MODEL_PATH).use { true }
    }.getOrDefault(false)

    private fun hasBundledBaseModelAsset(): Boolean = runCatching {
        context.assets.open(ASSET_BASE_MODEL_PATH).use { true }
    }.getOrDefault(false)

    private fun copyBundledAssetToFile(targetFile: File) {
        copyAssetToFile(
            assetPath = ASSET_SMALL_MODEL_PATH,
            targetFile = targetFile,
        )
    }

    private fun copyAssetToFile(
        assetPath: String,
        targetFile: File,
    ) {
        context.assets.open(assetPath).buffered().use { input ->
            targetFile.outputStream().buffered().use { output ->
                input.copyTo(output)
                output.flush()
            }
        }
    }

    private suspend fun downloadToFile(
        url: String,
        targetFile: File,
        onProgress: suspend (Int?) -> Unit,
    ) {
        val connection = (withContext(Dispatchers.IO) {
            URL(url).openConnection()
        } as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            requestMethod = "GET"
            instanceFollowRedirects = true
        }

        connection.connect()
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IOException("HTTP $code beim Modell-Download")
            }

            val totalLength = connection.contentLengthLong.takeIf { it > 0L }
            connection.inputStream.buffered().use { input ->
                targetFile.outputStream().buffered().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var downloaded = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        downloaded += read

                        val progress = totalLength?.let { total ->
                            ((downloaded.toDouble() / total.toDouble()) * 100.0)
                                .coerceIn(0.0, 100.0)
                                .roundToInt()
                        }
                        onProgress(progress)
                    }
                    output.flush()
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val PREFS_NAME = "whisper_model_preferences"
        const val KEY_SELECTED_MODEL = "selected_model"
        const val MODELS_DIR_NAME = "models"
        const val BASE_MODEL_FILE_NAME = "ggml-base.bin"
        const val SMALL_MODEL_FILE_NAME = "ggml-small-q5_1.bin"
        const val ASSET_BASE_MODEL_PATH = "models/ggml-base.bin"
        const val ASSET_SMALL_MODEL_PATH = "models/ggml-small-q5_1.bin"
    }
}


