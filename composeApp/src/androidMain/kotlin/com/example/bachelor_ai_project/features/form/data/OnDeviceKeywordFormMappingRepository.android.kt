package com.example.bachelor_ai_project.features.form.data

import com.example.bachelor_ai_project.core.result.AppResult
import com.example.bachelor_ai_project.core.result.runCatchingResult
import com.example.bachelor_ai_project.features.form.domain.FormDefinitionProvider
import com.example.bachelor_ai_project.features.form.domain.FormMappingRepository
import com.example.bachelor_ai_project.features.form.domain.MappingStrategy
import com.example.bachelor_ai_project.features.form.domain.TranscriptMappingResult
import com.example.bachelor_ai_project.features.form.domain.TranscriptToFormMapper
import com.example.bachelor_ai_project.features.transcription.domain.TranscriptionResponse

/**
 * Erste lokale Android-Variante für Formularautomatisierung ohne Cloud-Call.
 *
 * Die Extraktion ist bewusst konservativ und nutzt einfache Regex-/Keyword-Heuristiken.
 */
class OnDeviceKeywordFormMappingRepository(
    private val definitionProvider: FormDefinitionProvider,
) : FormMappingRepository {

    private val mapper = TranscriptToFormMapper()

    override suspend fun mapTranscript(response: TranscriptionResponse): AppResult<TranscriptMappingResult> =
        runCatchingResult {
            val speakerBlocks = mapper.map(response).speakerBlocks
            val transcript = buildString {
                if (speakerBlocks.isNotEmpty()) {
                    append(speakerBlocks.joinToString("\n") { block ->
                        "[${block.speaker.ifBlank { "Sprecher" }}]: ${block.text}"
                    })
                }
                if (response.text.isNotBlank()) {
                    if (isNotEmpty()) append("\n")
                    append(response.text)
                }
            }

            val answers = definitionProvider.questions
                .mapNotNull { question ->
                    val value = extractForQuestion(question.id, transcript)
                    if (value.isBlank()) null else question.id to value
                }
                .toMap()

            TranscriptMappingResult(
                speakerBlocks = speakerBlocks,
                fieldAnswers = answers,
                mappingStrategy = MappingStrategy.HEURISTIC_FALLBACK,
            )
        }

    private fun extractForQuestion(questionId: String, transcript: String): String {
        if (transcript.isBlank()) return ""
        val normalized = transcript.lowercase()
        val candidateSentences = transcript
            .replace("\n", " ")
            .split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        return when (questionId) {
            "name" -> {
                findFirstGroup(
                    text = transcript,
                    pattern = """(?i)(?:ich\s+hei(?:ss|\u00DF)e?|mein\s+name\s+ist|ich\s+bin(?:\s+(?:der|die))?)\s+([\p{L}][\p{L}'-]{1,30}(?:\s+[\p{L}][\p{L}'-]{1,30}){0,2})""",
                ) ?: ""
            }

            "problem" -> {
                findSentenceAfterKeywords(
                    text = transcript,
                    keywords = listOf("problem", "schwierig", "herausforderung", "fehler", "thema", "konflikt", "lief nicht"),
                )
                    ?: candidateSentences.maxByOrNull { it.length }?.take(220)
                    ?: ""
            }

            "learning" -> {
                findSentenceAfterKeywords(
                    text = transcript,
                    keywords = listOf("gelernt", "mitgenommen", "nächstes mal", "naechstes mal", "verbessern", "werde", "zukünftig", "zukunft"),
                )
                    ?: candidateSentences.firstOrNull { sentence ->
                        val lower = sentence.lowercase()
                        lower.contains("ich werde") || lower.contains("wir werden") || lower.contains("ich möchte") || lower.contains("ich moechte")
                    }?.take(220)
                    ?: if (normalized.contains("gelernt")) "Es wurde ein Lernerfolg beschrieben." else ""
            }

            else -> ""
        }
    }

    private fun findFirstGroup(text: String, pattern: String): String? =
        Regex(pattern).find(text)?.groupValues?.getOrNull(1)?.trim()?.trimEnd('.', ',', ';', ':')

    private fun findSentenceAfterKeywords(text: String, keywords: List<String>): String? {
        val segments = text
            .replace("\n", " ")
            .split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        return segments.firstOrNull { sentence ->
            val lower = sentence.lowercase()
            keywords.any { keyword -> lower.contains(keyword) }
        }?.take(220)
    }
}

