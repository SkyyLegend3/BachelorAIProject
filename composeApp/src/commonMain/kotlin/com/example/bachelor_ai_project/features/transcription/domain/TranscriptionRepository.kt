package com.example.bachelor_ai_project.features.transcription.domain

import com.example.bachelor_ai_project.core.result.AppResult

/**
 * Schnittstelle für die Transkription einer Audiodatei.
 * Entkoppelt das ViewModel von der konkreten API-Implementierung.
 */
interface TranscriptionRepository {
    /**
     * Transkribiert die Audiodatei am angegebenen Pfad.
     * @return [AppResult.Success] mit den Segmenten oder [AppResult.Error] bei Fehlern.
     */
    suspend fun transcribe(audioFilePath: String): AppResult<TranscriptionResponse>
}

