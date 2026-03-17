package com.example.bachelor_ai_project.features.form.data

import com.example.bachelor_ai_project.core.result.AppResult
import com.example.bachelor_ai_project.core.result.runCatchingResult
import com.example.bachelor_ai_project.features.form.domain.FormDefinitionProvider
import com.example.bachelor_ai_project.features.form.domain.FormMappingRepository
import com.example.bachelor_ai_project.features.form.domain.LlmFieldMappingResponse
import com.example.bachelor_ai_project.features.form.domain.OnDeviceFormMappingConfigurable
import com.example.bachelor_ai_project.features.form.domain.TranscriptMappingResult
import com.example.bachelor_ai_project.features.form.domain.TranscriptToFormMapper
import com.example.bachelor_ai_project.features.transcription.domain.TranscriptionResponse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/**
 * Android-On-Device-Repository fuer lokales LLM-Mapping.
 *
 * Wenn keine lokale LLM-Engine bereitsteht oder die Antwort ungueltig ist,
 * wird auf den bestehenden Keyword-Fallback zurueckgegriffen.
 */
class OnDeviceLlmFormMappingRepository(
    private val definitionProvider: FormDefinitionProvider,
    private val llmEngine: OnDeviceLlmEngine?,
    private val fallbackRepository: FormMappingRepository,
) : FormMappingRepository, OnDeviceFormMappingConfigurable {

    private data class FieldSpec(
        val id: String,
        val label: String,
        val normalizedLabel: String,
        val keywords: Set<String>,
    )

    private val mapper = TranscriptToFormMapper()
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    private val knownFieldIds = definitionProvider.questions.map { it.id }.toSet()
    private val normalizedKeyToFieldId = buildMap {
        definitionProvider.questions.forEach { question ->
            put(normalizeKey(question.id), question.id)
            put(normalizeKey(question.label), question.id)
        }
    }
    private val fieldSpecs = definitionProvider.questions.map { question ->
        FieldSpec(
            id = question.id,
            label = question.label,
            normalizedLabel = normalizeKey(question.label),
            keywords = buildQuestionKeywords(question.id, question.label),
        )
    }
    private val knownFieldRegexAlternation = definitionProvider.questions
        .flatMap { listOf(it.id, it.label) }
        .map { Regex.escape(it) }
        .sortedByDescending { it.length }
        .joinToString("|")
    @Volatile
    private var orthographyCorrectionEnabled: Boolean = true

    override fun setOrthographyCorrectionEnabled(enabled: Boolean) {
        orthographyCorrectionEnabled = enabled
    }

    override suspend fun mapTranscript(response: TranscriptionResponse): AppResult<TranscriptMappingResult> {
        val correctionEnabled = orthographyCorrectionEnabled
        val primaryResult = runCatchingResult {
            val speakerBlocks = mapper.map(response).speakerBlocks
            val transcriptTextWithSpeakers = if (speakerBlocks.isNotEmpty()) {
                speakerBlocks.joinToString("\n") { block ->
                    "[${block.speaker.ifBlank { "Sprecher" }}]: ${block.text}"
                }
            } else {
                ""
            }

            val transcriptTextFromSegments = response.segments
                .joinToString(" ") { it.text.trim() }
                .trim()

            val transcriptTextFromWords = response.words
                .joinToString(" ") { it.word.trim() }
                .trim()

            val transcriptTextPlain = response.text.ifBlank {
                if (transcriptTextFromSegments.isNotBlank()) {
                    transcriptTextFromSegments
                } else if (speakerBlocks.isNotEmpty()) {
                    speakerBlocks.joinToString(" ") { it.text.trim() }.trim()
                } else {
                    transcriptTextFromWords
                }
            }

            if (transcriptTextPlain.isBlank() && transcriptTextWithSpeakers.isBlank()) {
                when (val fallback = fallbackRepository.mapTranscript(response)) {
                    is AppResult.Success -> {
                        return@runCatchingResult fallback.data
                    }
                    is AppResult.Error -> {
                        return@runCatchingResult TranscriptMappingResult(
                            speakerBlocks = speakerBlocks,
                            fieldAnswers = emptyMap(),
                        )
                    }
                }
            }

            val heuristicAnswers = extractHeuristicAnswers(
                transcriptText = if (transcriptTextPlain.isNotBlank()) transcriptTextPlain else transcriptTextWithSpeakers,
                speakerBlocks = speakerBlocks,
            )

            val questionsDescription = definitionProvider.questions.joinToString("\n") { q ->
                "- ${q.id}: ${q.label}"
            }
            val answersJsonTemplate = definitionProvider.questions.joinToString(",") { q ->
                "\"${q.id}\":\"\""
            }

            val systemPrompt = """
                Rolle: Du bist ein JSON-Extraktor fuer ein Feedback-Formular.

                Ausgabevorschrift (SEHR WICHTIG):
                - Gib GENAU EINE ZEILE valides JSON zurueck.
                - Kein Markdown, keine Backticks, keine Erklaerung, kein Zusatztext.
                - Nutze EXAKT dieses Schema:
                  {"answers":{$answersJsonTemplate}}
                - Die Keys muessen EXAKT den Formularfeld-IDs entsprechen.
                - Wenn Information fehlt: leerer String "".

                Extraktionsregeln:
                - Nutze nur explizite Informationen aus dem Transkript.
                - Erfinde nichts.
                - Sprache der Werte: Deutsch.
                - Halte Werte kurz und konkret (maximal 220 Zeichen pro Feld).
                - Wenn im Dialog eine Frage gestellt wird (z.B. vom Interviewer), uebernimm als Feldwert die inhaltliche Antwort des Gegenuebers, NICHT die Frage selbst.
                - Frageformulierungen koennen variieren. Ordne nach inhaltlicher Bedeutung zur passenden Formularfrage zu.
                - Fuer Felder, die nach Name fragen, gib nur den Namen zurueck (ohne Zusatzsaetze).
                - Uebernimm pro Feld nur den inhaltlich passenden Teil der Antwort.
                - Wenn eine Antwort mehrere Themen enthaelt, entferne irrelevante Teilsaetze (z. B. private Vorlieben/Hobbys), sofern sie nicht zur Formularfrage gehoeren.
                - Wenn keine klar passende Information vorliegt, gib fuer das Feld einen leeren String zurueck.
                ${buildOrthographyInstruction(correctionEnabled)}
            """.trimIndent()

            val userPrompt = """
                Aufgabe: Fuelle die Formularfelder aus dem folgenden Gespraech.

                Formularfelder:
                $questionsDescription

                Transkript (zwischen den Tags):
                <transkript>
                ${if (transcriptTextWithSpeakers.isNotBlank()) transcriptTextWithSpeakers else transcriptTextPlain}
                </transkript>

                Antworte jetzt strikt im JSON-Schema.
            """.trimIndent()

            val llmAnswers = llmEngine?.let { engine ->
                runCatching {
                    val raw = engine.completeJson(systemPrompt = systemPrompt, userPrompt = userPrompt)
                    extractAnswers(raw)
                }.getOrElse { error ->
                    println("DEBUG OnDeviceLlmFormMappingRepository: LLM-Fehler, nutze Heuristik/Fallback: ${error.message}")
                    emptyMap()
                }
            } ?: emptyMap()

            val fallbackAnswers = when (val fallback = fallbackRepository.mapTranscript(response)) {
                is AppResult.Success -> fallback.data.fieldAnswers
                is AppResult.Error -> emptyMap()
            }

            val sanitizedLlmAnswers = sanitizeLlmAnswers(llmAnswers)
            val answers = mergeAnswersPerField(
                llmAnswers = sanitizedLlmAnswers,
                heuristicAnswers = heuristicAnswers,
                fallbackAnswers = fallbackAnswers,
            )

            println(
                "DEBUG OnDeviceLlmFormMappingRepository: answers llm=${sanitizedLlmAnswers.keys} " +
                    "heuristic=${heuristicAnswers.keys} fallback=${fallbackAnswers.keys} final=${answers.keys}"
            )
            println("DEBUG OnDeviceLlmFormMappingRepository: orthographyCorrectionEnabled=$correctionEnabled")

            TranscriptMappingResult(
                speakerBlocks = speakerBlocks,
                fieldAnswers = answers,
            )
        }

        return when (primaryResult) {
            is AppResult.Success -> primaryResult
            is AppResult.Error -> {
                when (val fallback = fallbackRepository.mapTranscript(response)) {
                    is AppResult.Success -> {
                        if (fallback.data.fieldAnswers.isNotEmpty()) {
                            fallback
                        } else {
                            AppResult.Error(primaryResult.message, primaryResult.cause)
                        }
                    }
                    is AppResult.Error -> AppResult.Error(primaryResult.message, primaryResult.cause)
                }
            }
        }
    }

    private fun extractAnswers(raw: String): Map<String, String> {
        val candidates = buildJsonCandidates(raw)
        for (candidate in candidates) {
            val parsed = parseAnswersCandidate(candidate)
            if (parsed.isNotEmpty()) return parsed
        }
        return emptyMap()
    }

    private fun buildJsonCandidates(raw: String): List<String> {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return emptyList()

        val fenced = Regex("""```(?:json)?\s*({[\s\S]*?})\s*```""")
            .findAll(trimmed)
            .map { it.groupValues[1].trim() }
        val balanced = extractBalancedJsonObjects(trimmed)

        return buildList {
            add(trimmed)
            addAll(fenced)
            addAll(balanced)
        }.distinct()
    }

    private fun parseAnswersCandidate(candidate: String): Map<String, String> {
        val cleaned = candidate.trim()
        if (cleaned.isBlank()) return emptyMap()

        runCatching {
            val response = json.decodeFromString<LlmFieldMappingResponse>(cleaned)
            return normalizeAnswers(response.answers)
        }

        val rootObject = runCatching { json.parseToJsonElement(cleaned).jsonObject }.getOrNull() ?: return emptyMap()
        val answersObject = rootObject["answers"]?.jsonObject

        val candidateAnswers = when {
            answersObject != null -> answersObject.toStringMap()
            else -> rootObject.toStringMap()
        }

        val normalizedFromJson = normalizeAnswers(candidateAnswers)
        if (normalizedFromJson.isNotEmpty()) return normalizedFromJson

        return parseKeyValueFallback(cleaned)
    }

    private fun parseKeyValueFallback(text: String): Map<String, String> {
        if (knownFieldRegexAlternation.isBlank()) return emptyMap()

        val lineBased = mutableMapOf<String, String>()
        Regex("""(?im)^\s*["']?($knownFieldRegexAlternation)["']?\s*[:=-]\s*(.+)$""")
            .findAll(text)
            .forEach { match ->
                val key = match.groupValues[1].trim().lowercase()
                val value = match.groupValues[2].trim().trim('"')
                if (value.isNotBlank()) lineBased[key] = value
            }

        if (lineBased.isNotEmpty()) return normalizeAnswers(lineBased)

        val inlineBased = mutableMapOf<String, String>()
        Regex("""(?i)["']?($knownFieldRegexAlternation)["']?\s*[:=-]\s*([^,;\n]+)""")
            .findAll(text)
            .forEach { match ->
                val key = match.groupValues[1].trim().lowercase()
                val value = match.groupValues[2].trim().trim('"')
                if (value.isNotBlank()) inlineBased[key] = value
            }

        return normalizeAnswers(inlineBased)
    }

    private fun JsonObject.toStringMap(): Map<String, String> = entries.mapNotNull { (key, value) ->
        val primitive = value as? JsonPrimitive ?: return@mapNotNull null
        primitive.contentOrNull?.let { key to it }
    }.toMap()

    private fun normalizeAnswers(rawAnswers: Map<String, String>): Map<String, String> {
        if (rawAnswers.isEmpty()) return emptyMap()

        return rawAnswers.mapNotNull { (rawKey, rawValue) ->
            val value = rawValue.trim()
            if (value.isBlank()) return@mapNotNull null

            val keyTrimmed = rawKey.trim()
            val mappedId = when {
                knownFieldIds.contains(keyTrimmed) -> keyTrimmed
                knownFieldIds.contains(keyTrimmed.lowercase()) -> keyTrimmed.lowercase()
                else -> normalizedKeyToFieldId[normalizeKey(keyTrimmed)]
            } ?: return@mapNotNull null

            mappedId to value
        }.toMap()
    }

    private fun sanitizeLlmAnswers(llmAnswers: Map<String, String>): Map<String, String> {
        if (llmAnswers.isEmpty()) return emptyMap()

        val normalizedValues = llmAnswers.values
            .map { normalizeAnswerValue(it) }
            .filter { it.isNotBlank() }

        if (normalizedValues.size >= 2 && normalizedValues.distinct().size == 1) {
            println("DEBUG OnDeviceLlmFormMappingRepository: Verwerfe LLM-Antworten, da alle Felder denselben Wert enthalten")
            return emptyMap()
        }

        return llmAnswers
    }

    private fun mergeAnswersPerField(
        llmAnswers: Map<String, String>,
        heuristicAnswers: Map<String, String>,
        fallbackAnswers: Map<String, String>,
    ): Map<String, String> {
        val result = mutableMapOf<String, String>()
        val usedNormalizedAnswers = mutableSetOf<String>()

        fieldSpecs.forEach { field ->
            val candidates = listOfNotNull(
                llmAnswers[field.id]?.trim(),
                heuristicAnswers[field.id]?.trim(),
                fallbackAnswers[field.id]?.trim(),
            ).map { candidate ->
                normalizeCandidateForField(field = field, candidate = candidate)
            }

            val picked = candidates.firstOrNull { candidate ->
                isUsableFieldAnswer(
                    field = field,
                    answer = candidate,
                    usedNormalizedAnswers = usedNormalizedAnswers,
                )
            }

            if (!picked.isNullOrBlank()) {
                result[field.id] = picked
                usedNormalizedAnswers += normalizeAnswerValue(picked)
            }
        }

        return result
    }

    private fun isUsableFieldAnswer(
        field: FieldSpec,
        answer: String,
        usedNormalizedAnswers: Set<String>,
    ): Boolean {
        val trimmed = answer.trim()
        if (trimmed.isBlank()) return false
        if (looksLikeQuestionEcho(trimmed, field)) return false

        val normalized = normalizeAnswerValue(trimmed)
        if (normalized.isBlank()) return false

        if (isNameField(field)) {
            val wordCount = trimmed.split(Regex("\\s+")).count { it.isNotBlank() }
            if (wordCount > 4 || trimmed.length > 64) return false
            if (trimmed.contains('?') || trimmed.contains('.') || trimmed.contains(':')) return false
        }

        if (!isNameField(field) && isQuestionLike(trimmed)) return false
        if (!isNameField(field) && normalized == field.normalizedLabel) return false
        if (!isNameField(field) && normalized in usedNormalizedAnswers) return false

        return true
    }

    private fun normalizeCandidateForField(field: FieldSpec, candidate: String): String {
        if (!isNameField(field)) {
            return trimIrrelevantTailForField(field = field, candidate = candidate.trim())
        }
        return extractNameFromText(candidate) ?: candidate.trim()
    }

    private fun trimIrrelevantTailForField(field: FieldSpec, candidate: String): String {
        if (candidate.isBlank()) return candidate
        if (!isLearningLikeField(field)) return candidate

        val clauses = candidate
            .split(Regex("""(?i)\s*(?:,\s*)?(?:und|ausserdem|zudem|uebrigens)\s+"""))
            .map { it.trim().trim(',', ';') }
            .filter { it.isNotBlank() }

        if (clauses.size <= 1) return candidate

        val scored = clauses.map { clause -> clause to scoreClauseForField(clause, field) }
        val best = scored.maxByOrNull { it.second } ?: return candidate
        val bestScore = best.second
        val secondBestScore = scored.map { it.second }.sortedDescending().getOrElse(1) { 0 }

        // Nur klar besseren Teil uebernehmen, sonst Original belassen.
        return if (bestScore >= 2 && bestScore > secondBestScore) best.first else candidate
    }

    private fun isLearningLikeField(field: FieldSpec): Boolean {
        val idNorm = normalizeKey(field.id)
        val labelNorm = field.normalizedLabel
        return idNorm.contains("learn") ||
            idNorm.contains("lesson") ||
            labelNorm.contains("gelernt") ||
            labelNorm.contains("lernen") ||
            labelNorm.contains("mitgenommen") ||
            labelNorm.contains("naechstes") ||
            labelNorm.contains("verbessern")
    }

    private fun scoreClauseForField(clause: String, field: FieldSpec): Int {
        val norm = normalizeKey(clause)
        if (norm.isBlank()) return 0

        var score = 0
        field.keywords.forEach { token ->
            if (token.isNotBlank() && norm.contains(token)) score += 2
        }

        val learningSignals = listOf(
            "gelernt", "mitgenommen", "naechstes", "verbessern", "sollte", "werde", "kuenftig", "zukunft"
        )
        if (learningSignals.any { signal -> norm.contains(normalizeKey(signal)) }) {
            score += 2
        }

        val offTopicSignals = listOf("fussball", "hobby", "mag", "liebe", "spiele", "musik", "urlaub")
        if (offTopicSignals.any { signal -> norm.contains(normalizeKey(signal)) }) {
            score -= 2
        }

        return score
    }

    private fun normalizeAnswerValue(value: String): String = value
        .lowercase()
        .replace("ä", "ae")
        .replace("ö", "oe")
        .replace("ü", "ue")
        .replace("ß", "ss")
        .replace(Regex("[^a-z0-9]+"), "")

    private fun normalizeKey(value: String): String = value
        .lowercase()
        .replace("ä", "ae")
        .replace("ö", "oe")
        .replace("ü", "ue")
        .replace("ß", "ss")
        .replace(Regex("[^a-z0-9]"), "")

    private fun extractBalancedJsonObjects(text: String): List<String> {
        val result = mutableListOf<String>()
        var depth = 0
        var start = -1
        var inString = false
        var escaped = false

        for (index in text.indices) {
            val ch = text[index]

            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (ch == '\\') {
                    escaped = true
                } else if (ch == '"') {
                    inString = false
                }
                continue
            }

            if (ch == '"') {
                inString = true
                continue
            }

            if (ch == '{') {
                if (depth == 0) start = index
                depth++
                continue
            }

            if (ch == '}') {
                if (depth == 0) continue
                depth--
                if (depth == 0) {
                    result += text.substring(start, index + 1)
                    start = -1
                }
            }
        }

        return result
    }

    private fun extractHeuristicAnswers(
        transcriptText: String,
        speakerBlocks: List<com.example.bachelor_ai_project.features.form.domain.SpeakerBlock>,
    ): Map<String, String> {
        val answers = mutableMapOf<String, String>()
        val sentences = transcriptText
            .replace("\n", " ")
            .split(Regex("(?<=[.!?])\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val name = extractNameFromText(transcriptText)
        if (!name.isNullOrBlank()) {
            fieldSpecs.firstOrNull { isNameField(it) }?.let { nameField ->
                answers[nameField.id] = name
            }
        }

        fieldSpecs.forEach { field ->
            if (answers.containsKey(field.id)) return@forEach

            val answerFromInlineQa = extractAnswerAfterQuestionInTranscript(
                transcriptText = transcriptText,
                field = field,
            )

            val answerFromTurnTransition = extractAnswerFromQuestionTransition(
                speakerBlocks = speakerBlocks,
                field = field,
            )

            val fallbackSentence = findBestAnswerSentence(
                sentences = sentences,
                field = field,
            )

            val picked = answerFromInlineQa ?: answerFromTurnTransition ?: fallbackSentence
            if (!picked.isNullOrBlank()) {
                answers[field.id] = picked.take(240)
            }
        }

        return answers
    }

    private fun extractAnswerFromQuestionTransition(
        speakerBlocks: List<com.example.bachelor_ai_project.features.form.domain.SpeakerBlock>,
        field: FieldSpec,
    ): String? {
        if (speakerBlocks.size < 2) return null

        for (index in 0 until speakerBlocks.lastIndex) {
            val currentBlock = speakerBlocks[index]
            val nextBlock = speakerBlocks[index + 1]
            val current = currentBlock.text.trim()
            val next = nextBlock.text.trim()
            if (current.isBlank() || next.isBlank()) continue

            if (!isQuestionLike(current)) continue
            if (!matchesFieldQuestion(current, field)) continue

            val candidate = when {
                !isQuestionLike(next) && currentBlock.speaker != nextBlock.speaker -> next
                index + 2 <= speakerBlocks.lastIndex -> {
                    val thirdBlock = speakerBlocks[index + 2]
                    val third = thirdBlock.text.trim()
                    if (
                        third.isNotBlank() &&
                        !isQuestionLike(third) &&
                        thirdBlock.speaker != currentBlock.speaker
                    ) {
                        third
                    } else {
                        null
                    }
                }
                else -> null
            }

            if (candidate.isNullOrBlank() || candidate.length < 8) continue
            if (looksLikeQuestionEcho(candidate, field)) continue

            return candidate.take(240)
        }

        return null
    }

    private fun isNameField(field: FieldSpec): Boolean {
        val idNorm = normalizeKey(field.id)
        return idNorm.contains("name") || field.normalizedLabel.contains("heiss") || field.normalizedLabel.contains("name")
    }

    private fun findBestAnswerSentence(
        sentences: List<String>,
        field: FieldSpec,
    ): String? {
        if (sentences.isEmpty()) return null

        val candidates = sentences
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filterNot { isQuestionLike(it) }
            .filterNot { looksLikeQuestionEcho(it, field) }
            .map { sentence -> sentence to scoreSentenceForField(sentence, field) }
            .filter { (_, score) -> score >= 2 }
            .sortedByDescending { (_, score) -> score }
            .toList()

        return candidates.firstOrNull()?.first?.take(240)
    }

    private fun extractAnswerAfterQuestionInTranscript(
        transcriptText: String,
        field: FieldSpec,
    ): String? {
        val text = transcriptText.replace("\n", " ").trim()
        if (text.isBlank()) return null

        val segments = text.split("?")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (segments.size < 2) return null

        for (index in 0 until segments.lastIndex) {
            val questionSegment = segments[index]
            if (!matchesFieldQuestion(questionSegment, field)) continue

            val rawAnswerSegment = segments[index + 1]
            val cleanedAnswer = cleanInlineAnswerSegment(rawAnswerSegment)
            if (cleanedAnswer.length < 6) continue
            if (looksLikeQuestionEcho(cleanedAnswer, field)) continue
            if (isQuestionLike(cleanedAnswer)) continue

            return cleanedAnswer.take(240)
        }

        return null
    }

    private fun cleanInlineAnswerSegment(segment: String): String {
        val withoutSpeakerPrefix = segment
            .replace(Regex("""^\s*\[[^\]]+\]\s*:\s*"""), "")
            .replace(Regex("""^\s*(sprecher\s*\d+|speaker\s*\d+)\s*[:\-]\s*""", RegexOption.IGNORE_CASE), "")
            .trim()

        // Wenn im Segment schon die naechste Frage startet, nehmen wir nur den Teil davor.
        val splitByQuestionStarter = Regex(
            pattern = """(?i)\b(was|wie|warum|wieso|weshalb|welche|welcher|wer)\b[^.?!]{0,120}$"""
        ).find(withoutSpeakerPrefix)

        val cut = splitByQuestionStarter?.range?.first ?: withoutSpeakerPrefix.length
        return withoutSpeakerPrefix.substring(0, cut).trim().trimEnd('.', ';', ',')
    }

    private fun scoreSentenceForField(sentence: String, field: FieldSpec): Int {
        val normSentence = normalizeKey(sentence)
        var score = 0

        field.keywords.forEach { token ->
            if (token.isNotBlank() && normSentence.contains(token)) {
                score += 3
            }
        }

        if (field.normalizedLabel.isNotBlank() && normSentence.contains(field.normalizedLabel)) {
            score += 2
        }

        if (isNameField(field)) {
            val compact = sentence.trim()
            if (compact.length <= 40 && !compact.contains('?') && !compact.contains('.') && compact.split(Regex("\\s+")).size <= 3) {
                score += 2
            }
        }
        return score
    }

    private fun buildQuestionKeywords(id: String, label: String): Set<String> {
        val stopWords = setOf(
            "was", "wie", "wer", "wo", "wann", "warum", "wieso", "weshalb",
            "ist", "sind", "war", "waren", "du", "ihr", "wir", "ich",
            "der", "die", "das", "dem", "den", "des", "ein", "eine", "einer",
            "und", "oder", "mit", "fuer", "fur", "aus", "bei", "von", "im", "in", "am", "an",
            "hast", "habt", "hat", "haben", "denn", "mal"
        )

        return ("$id $label")
            .lowercase()
            .replace("ä", "ae")
            .replace("ö", "oe")
            .replace("ü", "ue")
            .replace("ß", "ss")
            .split(Regex("[^a-z0-9]+"))
            .map { it.trim() }
            .filter { it.length >= 3 }
            .filterNot { stopWords.contains(it) }
            .toSet()
    }

    private fun isQuestionLike(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return false
        if (trimmed.contains('?')) return true

        val lower = trimmed.lowercase()
        val startsLikeQuestion = lower.startsWith("was ") ||
            lower.startsWith("wie ") ||
            lower.startsWith("warum ") ||
            lower.startsWith("wieso ") ||
            lower.startsWith("weshalb ") ||
            lower.startsWith("welche") ||
            lower.startsWith("welcher") ||
            lower.startsWith("kannst du") ||
            lower.startsWith("kannst du mir") ||
            lower.startsWith("erzaehl") ||
            lower.startsWith("erzähl")
        return startsLikeQuestion
    }

    private fun matchesFieldQuestion(questionText: String, field: FieldSpec): Boolean {
        val norm = normalizeKey(questionText)
        return field.keywords.any { token -> token.isNotBlank() && norm.contains(token) } ||
            (field.normalizedLabel.isNotBlank() && norm.contains(field.normalizedLabel))
    }

    private fun looksLikeQuestionEcho(candidateAnswer: String, field: FieldSpec): Boolean {
        val trimmed = candidateAnswer.trim()
        if (trimmed.isBlank()) return true
        if (trimmed.contains('?')) return true

        val normalized = normalizeKey(trimmed)
        if (normalized == field.normalizedLabel) return true
        val labelOverlap = field.keywords.count { token -> token.isNotBlank() && normalized.contains(token) }
        return labelOverlap >= 3 && normalized.length <= field.normalizedLabel.length + 16
    }

    private fun extractNameFromText(text: String): String? {
        if (text.isBlank()) return null

        val patterns = listOf(
            // Deckt u.a. "ich heiss", "ich heiß", "ich heiße", "mein Name ist" ab.
            """(?i)\b(?:ich\s+hei(?:ss|ß)e?|mein\s+name\s+ist|ich\s+bin(?:\s+(?:der|die))?)\s+([\p{L}][\p{L}'\-]{1,30}(?:\s+[\p{L}][\p{L}'\-]{1,30}){0,2})""",
            """(?i)\bname\s*[:\-]?\s*([\p{L}][\p{L}'\-]{1,30}(?:\s+[\p{L}][\p{L}'\-]{1,30}){0,2})""",
        )

        for (pattern in patterns) {
            val raw = Regex(pattern).find(text)?.groupValues?.getOrNull(1) ?: continue
            val cleaned = cleanupName(raw)
            if (!cleaned.isNullOrBlank()) return cleaned
        }

        return null
    }

    private fun cleanupName(raw: String): String? {
        val cleaned = raw
            .trim()
            .trim('.', ',', ';', ':', '!', '?')
            .replace(Regex("""^(?i)(der|die|das)\s+"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()

        if (cleaned.isBlank()) return null

        val parts = cleaned.split(" ").filter { it.isNotBlank() }
        if (parts.isEmpty() || parts.size > 3) return null
        if (parts.any { it.length < 2 }) return null

        return parts.joinToString(" ") { part ->
            part.lowercase().replaceFirstChar { ch -> ch.uppercase() }
        }
    }

    private fun buildOrthographyInstruction(enabled: Boolean): String {
        return if (enabled) {
            """
            - Vor der Feldzuordnung korrigiere offensichtliche ASR-/Whisper-Schreibfehler inhaltlich, wenn der gemeinte deutsche Begriff oder Name eindeutig ist.
            - Nutze dazu lautnahe Aehnlichkeit und den Gespraechskontext (z. B. Fachwort, Personenname, gaengige deutsche Schreibweise).
            - Korrigiere nur bei hoher Sicherheit. Bei Unsicherheit lieber Originaltext belassen.
            """.trimIndent()
        } else {
            "- Uebernehme Transkriptinhalte moeglichst woertlich und fuehre keine aktive Rechtschreibkorrektur durch."
        }
    }
}

