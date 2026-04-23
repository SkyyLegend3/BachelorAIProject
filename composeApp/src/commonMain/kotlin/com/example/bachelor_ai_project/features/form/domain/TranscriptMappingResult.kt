package com.example.bachelor_ai_project.features.form.domain

import com.example.bachelor_ai_project.features.transcription.domain.TranscriptionResponse

/**
 * Ein zusammengefasster Redeblock eines Sprechers aus dem Transkript.
 *
 * @param speaker       Sprecher-Label aus der Diarization (z.B. "SPEAKER_00").
 * @param text          Zusammengefasster Text aller Segmente dieses Blocks.
 * @param startSeconds  Startzeit des Blocks in Sekunden.
 * @param endSeconds    Endzeit des Blocks in Sekunden.
 */
data class SpeakerBlock(
    val speaker: String,
    val text: String,
    val startSeconds: Double,
    val endSeconds: Double,
)

/**
 * Kennzeichnet, welche Strategie das finale Feld-Mapping geliefert hat.
 */
enum class MappingStrategy {
    CLOUD_LLM,
    ON_DEVICE_LLM,
    MIXED,
    HEURISTIC_FALLBACK,
    UNKNOWN,
}

/**
 * Ergebnis des Mappings von [TranscriptionResponse] auf das Formular.
 *
 * @param speakerBlocks  Strukturierte Sprecher-Blöcke aus dem Transkript (für UI-Anzeige).
 * @param fieldAnswers   Map `questionId → vorausgefüllter Text` aus der KI-Zuordnung.
 * @param llmFailureReason Optionaler Diagnosegrund, falls LLM-Mapping nicht verwendet werden konnte.
 */
data class TranscriptMappingResult(
    val speakerBlocks: List<SpeakerBlock>,
    val fieldAnswers: Map<String, String>,
    val mappingStrategy: MappingStrategy = MappingStrategy.UNKNOWN,
    val llmFailureReason: String? = null,
    val llmAttempted: Boolean = false,
    val llmReturnedAnswers: Boolean = false,
)
