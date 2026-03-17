package com.example.bachelor_ai_project.features.transcription.data

import com.example.bachelor_ai_project.core.result.AppResult
import com.example.bachelor_ai_project.core.result.runCatchingResult
import com.example.bachelor_ai_project.features.transcription.domain.TranscriptSegment
import com.example.bachelor_ai_project.features.transcription.domain.TranscriptionRepository
import com.example.bachelor_ai_project.features.transcription.domain.TranscriptionResponse

/**
 * Lokale Transkription auf iOS via Swift-Bridge (whisper).
 */
class OnDeviceWhisperTranscriptionRepository(
    private val modelPath: String,
    private val bridge: IosWhisperBridge,
    private val language: String = "de",
) : TranscriptionRepository {

    override suspend fun transcribe(audioFilePath: String): AppResult<TranscriptionResponse> = runCatchingResult {
        require(audioFilePath.isNotBlank()) { "Audio-Datei-Pfad ist leer" }

        val raw = bridge.transcribe(
            modelPath = modelPath,
            audioFilePath = audioFilePath,
            language = language,
        ).trim()

        val bridgeError = bridge.lastErrorMessage().trim()

        require(raw.isNotBlank()) {
            if (bridgeError.isNotBlank()) {
                "Lokale Whisper-Transkription lieferte keine Ausgabe: $bridgeError"
            } else {
                "Lokale Whisper-Transkription lieferte keine Ausgabe"
            }
        }

        val segments = parseWhisperSegments(raw)
        val normalizedSegments = when {
            segments.isNotEmpty() -> segments
            else -> listOf(
                TranscriptSegment(
                    id = 0,
                    text = raw,
                    speaker = "SPEAKER_00",
                )
            )
        }

        val plainText = normalizedSegments.joinToString(" ") { it.text.trim() }.trim()
        val duration = normalizedSegments.maxOfOrNull { it.endSeconds } ?: 0.0

        TranscriptionResponse(
            text = plainText,
            language = language,
            duration = duration,
            segments = normalizedSegments,
            words = emptyList(),
        )
    }

    private fun parseWhisperSegments(raw: String): List<TranscriptSegment> {
        if (raw.isBlank()) return emptyList()

        val linePattern = Regex(
            """^\[(\d{2}:\d{2}:\d{2}\.\d{3})\s*-->\s*(\d{2}:\d{2}:\d{2}\.\d{3})]:\s*(.*)$"""
        )

        val segments = mutableListOf<TranscriptSegment>()
        raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { line ->
                val match = linePattern.find(line) ?: return@forEach
                val startSeconds = parseTimestampSeconds(match.groupValues[1])
                val endSeconds = parseTimestampSeconds(match.groupValues[2])
                val text = match.groupValues[3].trim()
                if (text.isBlank()) return@forEach

                segments += TranscriptSegment(
                    id = segments.size,
                    text = text,
                    startSeconds = startSeconds,
                    endSeconds = endSeconds,
                    speaker = "SPEAKER_00",
                )
            }

        return segments
    }

    private fun parseTimestampSeconds(value: String): Double {
        val parts = value.split(':', '.')
        if (parts.size != 4) return 0.0

        val hours = parts[0].toIntOrNull() ?: 0
        val minutes = parts[1].toIntOrNull() ?: 0
        val seconds = parts[2].toIntOrNull() ?: 0
        val millis = parts[3].toIntOrNull() ?: 0

        return hours * 3600.0 + minutes * 60.0 + seconds + millis / 1000.0
    }
}


