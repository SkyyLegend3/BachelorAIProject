package com.example.bachelor_ai_project.features.transcription.data

import com.example.bachelor_ai_project.core.config.AppConfig
import com.example.bachelor_ai_project.core.util.AndroidNativeRuntimeVerifier
import com.example.bachelor_ai_project.features.recording.domain.AppContextHolder
import com.example.bachelor_ai_project.features.transcription.domain.TranscriptionRepository

actual fun createOnDeviceTranscriptionRepository(): TranscriptionRepository? {
    val modelPath = AppConfig.whisperModelPath.trim()
    if (modelPath.isBlank()) return null
    if (!AndroidNativeRuntimeVerifier.ensureRealRuntime(
            componentTag = "OnDeviceTranscriptionRepositoryFactory(Android)",
            runtimeClassName = "com.whispercpp.whisper.WhisperContext",
        )) {
        return null
    }

    val manager = AndroidWhisperModelManager(
        context = AppContextHolder.applicationContext,
        baseModelPath = modelPath,
        smallModelDownloadUrl = AppConfig.whisperSmallModelDownloadUrl.trim(),
    )

    return OnDeviceWhisperTranscriptionRepository(
        modelPath = modelPath,
        modelManager = manager,
    )
}


