package com.example.bachelor_ai_project.features.transcription.data

import com.example.bachelor_ai_project.core.config.AppConfig
import com.example.bachelor_ai_project.features.transcription.domain.TranscriptionRepository

actual fun createOnDeviceTranscriptionRepository(): TranscriptionRepository? {
    val modelPath = AppConfig.whisperModelPath.trim()
    if (modelPath.isBlank()) return null

    return OnDeviceWhisperTranscriptionRepository(modelPath = modelPath)
}

