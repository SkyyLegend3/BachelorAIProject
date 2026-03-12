package com.example.bachelor_ai_project.features.transcription.domain

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Einzelnes Wort innerhalb eines Segments – geliefert wenn `timestamp_granularities=word`.
 */
@Serializable
data class TranscriptWord(
    val word: String,
    @SerialName("start") val startSeconds: Double = 0.0,
    @SerialName("end") val endSeconds: Double = 0.0,
)

/**
 * Ein Segment der Transkription.
 *
 * OpenAI liefert bei `response_format=verbose_json` und `diarize=true`
 * ein Feld `speaker` pro Segment (z.B. "SPEAKER_00", "SPEAKER_01").
 *
 * Alle Felder außer [id], [text] und [speaker] sind optional –
 * sie sind nur vorhanden wenn `timestamp_granularities` entsprechend gesetzt ist.
 */
@Serializable
data class TranscriptSegment(
    val id: Int = 0,
    val text: String,
    @SerialName("start") val startSeconds: Double = 0.0,
    @SerialName("end") val endSeconds: Double = 0.0,
    val speaker: String = "",
    @SerialName("avg_logprob") val avgLogprob: Double = 0.0,
    @SerialName("compression_ratio") val compressionRatio: Double = 0.0,
    @SerialName("no_speech_prob") val noSpeechProb: Double = 0.0,
    val words: List<TranscriptWord> = emptyList(),
    val tokens: List<Int> = emptyList(),
    val temperature: Double = 0.0,
    val seek: Int = 0,
)

/**
 * Vollständige Antwort der OpenAI Transcriptions-API
 * bei `response_format=verbose_json` mit Diarization.
 *
 * Referenz: https://platform.openai.com/docs/api-reference/audio/verbose-json-object
 */
@Serializable
data class TranscriptionResponse(
    /** Vollständiger Transkriptions-Text ohne Segmentierung. */
    val text: String = "",
    /** Erkannte Sprache (ISO-639-1, z.B. "de"). */
    val language: String = "",
    /** Dauer der Audiodatei in Sekunden. */
    val duration: Double = 0.0,
    /** Liste aller transkribierten Segmente mit Sprecher-Label. */
    val segments: List<TranscriptSegment> = emptyList(),
    /** Wort-Level-Timestamps (nur wenn `timestamp_granularities=word`). */
    val words: List<TranscriptWord> = emptyList(),
)

