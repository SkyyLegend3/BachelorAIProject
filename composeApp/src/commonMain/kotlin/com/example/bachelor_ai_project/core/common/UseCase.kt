package com.example.bachelor_ai_project.core.common

/**
 * Basis-Interface für alle Use-Cases im Projekt.
 *
 * Einheitliche Signatur: suspend [invoke] nimmt [Params] entgegen
 * und gibt [Result] zurück (typischerweise [AppResult]).
 *
 * Beispiel:
 * ```kotlin
 * class MapTranscriptToFormUseCase(...) : UseCase<TranscriptionResponse, AppResult<TranscriptMappingResult>> {
 *     override suspend fun invoke(params: TranscriptionResponse) = repository.mapTranscript(params)
 * }
 * ```
 */
interface UseCase<in Params, out Result> {
    suspend operator fun invoke(params: Params): Result
}

