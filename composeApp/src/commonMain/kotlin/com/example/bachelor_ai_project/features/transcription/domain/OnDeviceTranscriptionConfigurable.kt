package com.example.bachelor_ai_project.features.transcription.domain

import kotlinx.coroutines.flow.StateFlow

/**
 * Android-spezifische Optionen fuer die lokale Whisper-Konfiguration.
 */
enum class WhisperLocalModel {
    BASE,
    SMALL,
}

/**
 * UI-nahe Zustandsdaten fuer die Modellverwaltung.
 */
data class OnDeviceWhisperModelState(
    val supportsModelManagement: Boolean = false,
    val selectedModel: WhisperLocalModel = WhisperLocalModel.BASE,
    val isSmallModelDownloaded: Boolean = false,
    val canInstallSmallModelFromBundle: Boolean = false,
    val canDownloadSmallModel: Boolean = false,
    val isDownloadingSmallModel: Boolean = false,
    val smallModelDownloadProgressPercent: Int? = null,
    val smallModelStatusMessage: String? = null,
    val lastError: String? = null,
)

/**
 * Optionales Interface fuer Repositories mit konfigurierbarer Whisper-Modellauswahl.
 */
interface OnDeviceTranscriptionConfigurable {
    val modelState: StateFlow<OnDeviceWhisperModelState>

    suspend fun selectModel(model: WhisperLocalModel)

    suspend fun prepareSmallModel()
}

