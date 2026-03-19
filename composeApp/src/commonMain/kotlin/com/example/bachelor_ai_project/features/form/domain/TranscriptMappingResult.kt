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
 * Ergebnis des Mappings von [TranscriptionResponse] auf das Formular.
 *
 * @param speakerBlocks  Strukturierte Sprecher-Blöcke aus dem Transkript (für UI-Anzeige).
 * @param fieldAnswers   Map `questionId → vorausgefüllter Text` aus der KI-Zuordnung.
 * @param processLog     Kurzbeschreibung des verwendeten Mapping-Pfads (z.B. LLM/Heuristik).
 * @param processDetails Detaillierte Diagnose-Logs zum Mapping-Pfad (für UI-Debug-Ausgabe).
 */
data class TranscriptMappingResult(
    val speakerBlocks: List<SpeakerBlock>,
    val fieldAnswers: Map<String, String>,
    val processLog: String? = null,
    val processDetails: List<String> = emptyList(),
)

