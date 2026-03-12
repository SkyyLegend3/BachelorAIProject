package com.example.bachelor_ai_project.features.form.domain

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
 * Ergebnis des Mappings von [TranscriptionResponse] auf das Formular.
 *
 * @param speakerBlocks  Strukturierte Sprecher-Blöcke aus dem Transkript (für UI-Anzeige).
 * @param fieldAnswers   Map `questionId → vorausgefüllter Text` aus der KI-Zuordnung.
 */
data class TranscriptMappingResult(
    val speakerBlocks: List<SpeakerBlock>,
    val fieldAnswers: Map<String, String>,
)

