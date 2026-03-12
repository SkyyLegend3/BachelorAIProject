package com.example.bachelor_ai_project.features.form.domain

import com.example.bachelor_ai_project.features.transcription.domain.TranscriptionResponse

/**
 * Wandelt eine [TranscriptionResponse] in ein [TranscriptMappingResult] um.
 *
 * Aufeinanderfolgende Segmente desselben Sprechers werden zu einem [SpeakerBlock]
 * zusammengefasst. Das Ergebnis enthält die strukturierten Sprecher-Blöcke zur
 * Anzeige in der UI sowie eine Map `questionId → vorausgefüllter Text` für die
 * Formularfelder.
 */
class TranscriptToFormMapper {

    fun map(response: TranscriptionResponse): TranscriptMappingResult {
        val speakerBlocks = buildSpeakerBlocks(response)
        return TranscriptMappingResult(
            speakerBlocks = speakerBlocks,
            fieldAnswers = emptyMap(),
        )
    }

    /**
     * Fasst aufeinander folgende Segmente desselben Sprechers zu einem Block zusammen.
     * Wechselt der Sprecher, beginnt ein neuer Block.
     */
    private fun buildSpeakerBlocks(response: TranscriptionResponse): List<SpeakerBlock> {
        if (response.segments.isEmpty()) return emptyList()

        val blocks = mutableListOf<SpeakerBlock>()
        var currentSpeaker = response.segments.first().speaker
        val currentTexts = mutableListOf<String>()
        var blockStart = response.segments.first().startSeconds

        for (segment in response.segments) {
            if (segment.speaker != currentSpeaker) {
                blocks += SpeakerBlock(
                    speaker = currentSpeaker,
                    text = currentTexts.joinToString(" ").trim(),
                    startSeconds = blockStart,
                    endSeconds = segment.startSeconds,
                )
                currentSpeaker = segment.speaker
                currentTexts.clear()
                blockStart = segment.startSeconds
            }
            currentTexts += segment.text.trim()
        }

        // Letzten Block abschließen
        if (currentTexts.isNotEmpty()) {
            blocks += SpeakerBlock(
                speaker = currentSpeaker,
                text = currentTexts.joinToString(" ").trim(),
                startSeconds = blockStart,
                endSeconds = response.segments.last().endSeconds,
            )
        }

        return blocks
    }
}

