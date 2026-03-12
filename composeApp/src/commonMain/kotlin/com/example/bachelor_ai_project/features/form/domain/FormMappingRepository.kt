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

