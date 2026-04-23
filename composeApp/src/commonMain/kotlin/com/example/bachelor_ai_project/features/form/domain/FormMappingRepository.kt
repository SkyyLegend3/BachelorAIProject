package com.example.bachelor_ai_project.features.form.domain

import com.example.bachelor_ai_project.core.result.AppResult
import com.example.bachelor_ai_project.features.transcription.domain.TranscriptionResponse

/**
 * Kapselt das Mapping von [TranscriptionResponse] auf Formularfelder.
 *
 * Aktuell: rein strukturelles Mapping (Sprecher-Blöcke aufbauen).
 * Später: LLM-Aufruf, der [TranscriptMappingResult.fieldAnswers] befüllt.
 */
interface FormMappingRepository {
    suspend fun mapTranscript(response: TranscriptionResponse): AppResult<TranscriptMappingResult>
}

/**
 * Optionale Laufzeit-Konfiguration fuer On-Device-Mapping-Repositories.
 *
 * Cloud-Repositories muessen dieses Interface nicht implementieren.
 */
interface OnDeviceFormMappingConfigurable {
    fun setOrthographyCorrectionEnabled(enabled: Boolean)
}

/**
 * Optionales Interface, damit die UI den lokalen Modellstatus anzeigen kann.
 */
interface OnDeviceLlmModelStatusProvider {
    fun isOnDeviceLlmModelConfigured(): Boolean
    fun isOnDeviceLlmModelReady(): Boolean
    fun isOnDeviceLlmModelLoading(): Boolean = false
    suspend fun warmupOnDeviceLlmModel(): AppResult<Unit> = AppResult.Success(Unit)
    suspend fun runOnDeviceLlmSelfTest(): AppResult<Unit> =
        AppResult.Error("On-Device-LLM-Selbsttest nicht verfuegbar.")
}

