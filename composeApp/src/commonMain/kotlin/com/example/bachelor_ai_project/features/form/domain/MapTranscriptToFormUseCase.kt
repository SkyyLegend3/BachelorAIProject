package com.example.bachelor_ai_project.features.form.domain

import com.example.bachelor_ai_project.core.common.UseCase
import com.example.bachelor_ai_project.core.result.AppResult
import com.example.bachelor_ai_project.features.transcription.domain.TranscriptionResponse

/**
 * UseCase: Mappt eine [TranscriptionResponse] auf Formularfelder.
 *
 * Delegiert an [FormMappingRepository] und entkoppelt so das ViewModel
 * von der konkreten Repository-Implementierung.
 */
class MapTranscriptToFormUseCase(
    private val repository: FormMappingRepository,
) : UseCase<TranscriptionResponse, AppResult<TranscriptMappingResult>> {

    override suspend fun invoke(params: TranscriptionResponse): AppResult<TranscriptMappingResult> =
        repository.mapTranscript(params)
}

