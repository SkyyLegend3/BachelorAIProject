package com.example.bachelor_ai_project.features.transcription.domain

import com.example.bachelor_ai_project.core.common.UseCase
import com.example.bachelor_ai_project.core.result.AppResult

/**
 * UseCase: Transkribiert eine Audiodatei am angegebenen Pfad.
 *
 * Delegiert an [TranscriptionRepository] und entkoppelt so das ViewModel
 * von der konkreten Repository-Implementierung.
 */
class TranscribeAudioUseCase(
    private val repository: TranscriptionRepository,
) : UseCase<String, AppResult<TranscriptionResponse>> {

    override suspend fun invoke(params: String): AppResult<TranscriptionResponse> =
        repository.transcribe(params)
}

