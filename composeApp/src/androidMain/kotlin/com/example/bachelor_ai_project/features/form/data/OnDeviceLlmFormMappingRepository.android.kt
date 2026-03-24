package com.example.bachelor_ai_project.features.form.data

import com.example.bachelor_ai_project.core.config.AppConfig
import com.example.bachelor_ai_project.core.result.AppResult
import com.example.bachelor_ai_project.core.result.runCatchingResult
import com.example.bachelor_ai_project.features.form.domain.FormDefinitionProvider
import com.example.bachelor_ai_project.features.form.domain.FormQuestion
import com.example.bachelor_ai_project.features.form.domain.FormMappingRepository
import com.example.bachelor_ai_project.features.form.domain.LlmFieldMappingResponse
import com.example.bachelor_ai_project.features.form.domain.MappingStrategy
import com.example.bachelor_ai_project.features.form.domain.OnDeviceFormMappingConfigurable
import com.example.bachelor_ai_project.features.form.domain.OnDeviceLlmModelStatusProvider
import com.example.bachelor_ai_project.features.form.domain.SpeakerBlock
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
 * Strategie:
 * - Erst heuristische Kandidaten erzeugen
 * - Dann das LLM nur mit kompakten Prompt-Chunks fuer kleine Feldgruppen aufrufen
 * - Dann mit Heuristik/Fallback mergen
 */
class OnDeviceLlmFormMappingRepository(
    private val definitionProvider: FormDefinitionProvider,
    private val llmEngine: OnDeviceLlmEngine?,
    private val fallbackRepository: FormMappingRepository,
) : FormMappingRepository, OnDeviceFormMappingConfigurable, OnDeviceLlmModelStatusProvider {

    @Volatile
    private var activeLlmEngine: OnDeviceLlmEngine? = llmEngine
    private val llmEngineLock = Any()

    private data class FieldSpec(
        val id: String,
        val label: String,
        val normalizedLabel: String,
        val keywords: Set<String>,
    )

    private data class PromptBundle(
        val systemPrompt: String,
        val userPrompt: String,
    )

    private enum class CandidateSource {
        LLM,
        HEURISTIC,
        FALLBACK,
    }

    private data class MergeOutcome(
        val answers: Map<String, String>,
        val usedSources: Set<CandidateSource>,
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

    override fun isOnDeviceLlmModelConfigured(): Boolean {
        val modelPath = AndroidLlamaModelPathResolver.resolveExistingModelPath(AppConfig.llamaModelPath)
        return modelPath != null
    }

    override fun isOnDeviceLlmModelReady(): Boolean {
        if (!isOnDeviceLlmModelConfigured()) return false
        val engine = resolveLlmEngine() ?: return false
        val statusEngine = engine as? WarmupCapableOnDeviceLlmEngine ?: return true
        return statusEngine.isModelLoaded()
    }

    override fun isOnDeviceLlmModelLoading(): Boolean {
        val statusEngine = resolveLlmEngine() as? WarmupCapableOnDeviceLlmEngine ?: return false
        return statusEngine.isModelLoading()
    }

    override suspend fun warmupOnDeviceLlmModel(): AppResult<Unit> {
        if (!isOnDeviceLlmModelConfigured()) {
            return AppResult.Error("LLM-Model nicht gefunden oder Pfad ungueltig.")
        }
        val statusEngine = resolveLlmEngine() as? WarmupCapableOnDeviceLlmEngine
            ?: return AppResult.Error("On-Device-LLM-Engine nicht verfuegbar.")
        return statusEngine.warmupModel()
    }

    override suspend fun runOnDeviceLlmSelfTest(): AppResult<Unit> {
        if (!isOnDeviceLlmModelConfigured()) {
            return AppResult.Error("LLM-Model nicht gefunden oder Pfad ungueltig.")
        }

        val engine = resolveLlmEngine()
            ?: return AppResult.Error("On-Device-LLM-Engine nicht verfuegbar.")

        return runCatchingResult {
            val systemPrompt = """
                Du bist ein JSON-Testassistent.
                Antworte nur mit einem JSON-Objekt.
                Kein Markdown.
                Kein Zusatztext.
            """.trimIndent()

            val userPrompt = """
                Gib exakt dieses JSON zurueck:
                {"ok":"ja"}
            """.trimIndent()

            val raw = engine.completeJson(
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
            )

            println("DEBUG SelfTest raw=[$raw]")

            val normalized = raw.replace("\n", " ")
            val isOk = Regex("\"ok\"\\s*:\\s*\"ja\"", RegexOption.IGNORE_CASE)
                .containsMatchIn(normalized)

            require(isOk) {
                "LLM-Test fehlgeschlagen. Antwort: [$raw]"
            }
        }
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
                when {
                    transcriptTextFromSegments.isNotBlank() -> transcriptTextFromSegments
                    speakerBlocks.isNotEmpty() -> speakerBlocks.joinToString(" ") { it.text.trim() }.trim()
                    else -> transcriptTextFromWords
                }
            }

            if (transcriptTextPlain.isBlank() && transcriptTextWithSpeakers.isBlank()) {
                return@runCatchingResult when (val fallback = fallbackRepository.mapTranscript(response)) {
                    is AppResult.Success -> fallback.data
                    is AppResult.Error -> TranscriptMappingResult(
                        speakerBlocks = speakerBlocks,
                        fieldAnswers = emptyMap(),
                    )
                }
            }

            val transcriptForHeuristics =
                if (transcriptTextPlain.isNotBlank()) transcriptTextPlain else transcriptTextWithSpeakers

            val transcriptForLlmSource =
                if (transcriptTextWithSpeakers.isNotBlank()) transcriptTextWithSpeakers else transcriptTextPlain

            val heuristicAnswers = extractHeuristicAnswers(
                transcriptText = transcriptForHeuristics,
                speakerBlocks = speakerBlocks,
            )

            val fallbackAnswers = when (val fallback = fallbackRepository.mapTranscript(response)) {
                is AppResult.Success -> fallback.data.fieldAnswers
                is AppResult.Error -> emptyMap()
            }

            var llmFailureReason: String? = null
            var llmAttempted = false

            val llmAnswers = when (val engine = resolveLlmEngine()) {
                null -> {
                    llmFailureReason = "Keine On-Device-LLM-Engine verfuegbar (Bridge/Modell nicht initialisiert)."
                    emptyMap()
                }
                else -> {
                    val groupedAnswers = mutableMapOf<String, String>()
                    val questionGroups = buildQuestionGroups()

                    for ((groupIndex, questionGroup) in questionGroups.withIndex()) {
                        llmAttempted = true
                        val focusedTranscript = buildFocusedTranscript(
                            questions = questionGroup,
                            transcriptText = transcriptForLlmSource,
                            heuristicAnswers = heuristicAnswers,
                        )

                        val prompt = buildCompactPrompt(
                            questions = questionGroup,
                            transcript = focusedTranscript,
                            correctionEnabled = correctionEnabled,
                        )

                        println(
                            "DEBUG OnDeviceLlmFormMappingRepository: LLM chunk ${groupIndex + 1}/${questionGroups.size}, " +
                                    "questions=${questionGroup.map { it.id }}, transcriptChars=${focusedTranscript.length}"
                        )

                        val chunkStartedAt = System.currentTimeMillis()

                        val rawResult = runCatching {
                            engine.completeJson(
                                systemPrompt = prompt.systemPrompt,
                                userPrompt = prompt.userPrompt,
                            )
                        }

                        val chunkDuration = System.currentTimeMillis() - chunkStartedAt
                        println("DEBUG OnDeviceLlmFormMappingRepository: chunk ${groupIndex + 1} done in ${chunkDuration}ms")

                        if (rawResult.isFailure) {
                            val error = rawResult.exceptionOrNull()
                            llmFailureReason = "LLM-Aufruf fehlgeschlagen: ${error?.message ?: "unbekannter Fehler"}"
                            println(
                                "DEBUG OnDeviceLlmFormMappingRepository: LLM-Fehler in Chunk ${groupIndex + 1}: ${error?.message}"
                            )
                            println("DEBUG LLM error class=${error?.javaClass?.name}")
                            println("DEBUG LLM error message=${error?.message}")
                            println("DEBUG LLM error cause=${error?.cause?.javaClass?.name}: ${error?.cause?.message}")
                            continue
                        }

                        val raw = rawResult.getOrNull().orEmpty()
                        println("DEBUG OnDeviceLlmFormMappingRepository: raw chunk ${groupIndex + 1}=[$raw]")

                        val extracted = extractAnswers(raw)
                        if (extracted.isEmpty()) {
                            if (llmFailureReason.isNullOrBlank()) {
                                llmFailureReason = "LLM-Antwort war leer oder nicht im erwarteten JSON-Schema."
                            }
                        } else {
                            groupedAnswers.putAll(extracted)
                        }
                    }

                    groupedAnswers
                }
            }

            val sanitizedLlmAnswers = sanitizeLlmAnswers(llmAnswers)
            if (llmAnswers.isNotEmpty() && sanitizedLlmAnswers.isEmpty() && llmFailureReason.isNullOrBlank()) {
                llmFailureReason = "LLM-Antwort wurde als unbrauchbar verworfen."
            }

            val mergeOutcome = mergeAnswersPerField(
                llmAnswers = sanitizedLlmAnswers,
                heuristicAnswers = heuristicAnswers,
                fallbackAnswers = fallbackAnswers,
            )

            val answers = mergeOutcome.answers

            val mappingStrategy = when {
                CandidateSource.LLM in mergeOutcome.usedSources && mergeOutcome.usedSources.size == 1 ->
                    MappingStrategy.ON_DEVICE_LLM
                CandidateSource.LLM in mergeOutcome.usedSources ->
                    MappingStrategy.MIXED
                mergeOutcome.usedSources.isNotEmpty() ->
                    MappingStrategy.HEURISTIC_FALLBACK
                else ->
                    MappingStrategy.UNKNOWN
            }

            val effectiveLlmFailureReason = when (mappingStrategy) {
                MappingStrategy.ON_DEVICE_LLM,
                MappingStrategy.MIXED,
                MappingStrategy.CLOUD_LLM,
                    -> null

                MappingStrategy.HEURISTIC_FALLBACK,
                MappingStrategy.UNKNOWN,
                    -> llmFailureReason
            }

            println(
                "DEBUG OnDeviceLlmFormMappingRepository: answers llm=${sanitizedLlmAnswers.keys} " +
                        "heuristic=${heuristicAnswers.keys} fallback=${fallbackAnswers.keys} final=${answers.keys}"
            )
            println("DEBUG OnDeviceLlmFormMappingRepository: orthographyCorrectionEnabled=$correctionEnabled")

            TranscriptMappingResult(
                speakerBlocks = speakerBlocks,
                fieldAnswers = answers,
                mappingStrategy = mappingStrategy,
                llmFailureReason = effectiveLlmFailureReason,
                llmAttempted = llmAttempted,
                llmReturnedAnswers = sanitizedLlmAnswers.isNotEmpty(),
            )
        }

        return when (primaryResult) {
            is AppResult.Success -> primaryResult
            is AppResult.Error -> {
                when (val fallback = fallbackRepository.mapTranscript(response)) {
                    is AppResult.Success -> {
                        if (fallback.data.fieldAnswers.isNotEmpty()) {
                            AppResult.Success(
                                fallback.data.copy(
                                    llmFailureReason = primaryResult.message,
                                )
                            )
                        } else {
                            AppResult.Error(primaryResult.message, primaryResult.cause)
                        }
                    }
                    is AppResult.Error -> AppResult.Error(primaryResult.message, primaryResult.cause)
                }
            }
        }
    }

    private fun buildQuestionGroups(): List<List<FormQuestion>> {
        val questions = definitionProvider.questions
        if (questions.isEmpty()) return emptyList()

        if (questions.size <= SMALL_FORM_SINGLE_PASS_THRESHOLD) {
            return listOf(questions)
        }

        val nameQuestions = questions.filter { q ->
            val idNorm = normalizeKey(q.id)
            val labelNorm = normalizeKey(q.label)
            idNorm.contains("name") || labelNorm.contains("name") || labelNorm.contains("heiss")
        }

        val nonNameQuestions = questions - nameQuestions.toSet()

        val result = mutableListOf<List<FormQuestion>>()

        if (nameQuestions.isNotEmpty()) {
            result += nameQuestions
        }

        result += nonNameQuestions.chunked(1)

        return result
    }

    private fun buildCompactPrompt(
        questions: List<FormQuestion>,
        transcript: String,
        correctionEnabled: Boolean,
    ): PromptBundle {
        val questionsDescription = questions.joinToString("\n") { q ->
            "- ${q.id}: ${q.label}"
        }

        val answersJsonTemplate = questions.joinToString(",") { q ->
            "\"${q.id}\":\"\""
        }

        val orthographyInstruction = if (correctionEnabled) {
            "Korrigiere nur offensichtliche ASR-Fehler bei hoher Sicherheit."
        } else {
            "Keine aktive Rechtschreibkorrektur."
        }

        val systemPrompt = """
        Gib genau eine JSON-Zeile zurueck.
        Kein Markdown. Kein Zusatztext.
        Nutze nur sichere Infos aus dem Transkript.
        Antworte mit einem flachen JSON-Objekt.
        Erlaubte Keys: ${questions.joinToString(", ") { it.id }}.
        Werte kurz halten, maximal 6 Woerter.
        Name nur als Personenname.
        Unbekannte Felder weglassen.
        Beispiel: {$answersJsonTemplate}
        $orthographyInstruction
        """.trimIndent()

        val userPrompt = """
        Felder:
        $questionsDescription

        Text:
        ${transcript.take(MAX_TRANSCRIPT_CHARS_PER_CHUNK)}

        JSON:
        """.trimIndent()

        return PromptBundle(
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
        )
    }

    private fun buildFocusedTranscript(
        questions: List<FormQuestion>,
        transcriptText: String,
        heuristicAnswers: Map<String, String>,
    ): String {
        if (transcriptText.isBlank()) return ""

        val normalizedTranscript = transcriptText.trim()
        if (normalizedTranscript.length <= MAX_TRANSCRIPT_CHARS_PER_CHUNK) {
            return normalizedTranscript
        }

        val sentences = normalizedTranscript
            .replace("\n", " \n ")
            .split(Regex("(?<=[.!?])\\s+|\\n"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        if (sentences.isEmpty()) {
            return normalizedTranscript.take(MAX_TRANSCRIPT_CHARS_PER_CHUNK)
        }

        val wantedFieldIds = questions.map { it.id }.toSet()
        val relatedSpecs = fieldSpecs.filter { it.id in wantedFieldIds }

        val scored = sentences.map { sentence ->
            var score = 0
            val norm = normalizeKey(sentence)

            relatedSpecs.forEach { field ->
                field.keywords.forEach { token ->
                    if (token.isNotBlank() && norm.contains(token)) {
                        score += 3
                    }
                }
                if (field.normalizedLabel.isNotBlank() && norm.contains(field.normalizedLabel)) {
                    score += 2
                }
                heuristicAnswers[field.id]?.let { heuristic ->
                    val heuristicNorm = normalizeKey(heuristic)
                    if (heuristicNorm.isNotBlank() && norm.contains(heuristicNorm.take(24))) {
                        score += 2
                    }
                }
            }

            if (sentence.contains(":")) score += 1
            if (sentence.contains("?")) score += 1

            sentence to score
        }

        val picked = scored
            .sortedByDescending { it.second }
            .map { it.first }
            .distinct()
            .take(12)

        val joined = picked.joinToString("\n")
        return if (joined.isNotBlank()) {
            joined.take(MAX_TRANSCRIPT_CHARS_PER_CHUNK)
        } else {
            normalizedTranscript.take(MAX_TRANSCRIPT_CHARS_PER_CHUNK)
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

        val fenced = Regex("""```(?:json)?\s*(\{[\s\S]*?\})\s*```""")
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

        val rootObject = runCatching {
            json.parseToJsonElement(cleaned).jsonObject
        }.getOrNull()
        if (rootObject == null) {
            return parseKeyValueFallback(cleaned)
        }

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

    private fun JsonObject.toStringMap(): Map<String, String> =
        entries.mapNotNull { (key, value) ->
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
    ): MergeOutcome {
        val result = mutableMapOf<String, String>()
        val usedNormalizedAnswers = mutableSetOf<String>()
        val usedSources = mutableSetOf<CandidateSource>()

        fieldSpecs.forEach { field ->
            val candidates = listOfNotNull(
                llmAnswers[field.id]?.trim()?.let { CandidateSource.LLM to it },
                heuristicAnswers[field.id]?.trim()?.let { CandidateSource.HEURISTIC to it },
                fallbackAnswers[field.id]?.trim()?.let { CandidateSource.FALLBACK to it },
            ).map { (source, candidate) ->
                source to normalizeCandidateForField(field = field, candidate = candidate)
            }

            val picked = candidates.firstOrNull { (_, candidate) ->
                isUsableFieldAnswer(
                    field = field,
                    answer = candidate,
                    usedNormalizedAnswers = usedNormalizedAnswers,
                )
            }

            if (picked != null) {
                val (source, answer) = picked
                if (answer.isNotBlank()) {
                    result[field.id] = answer
                    usedSources += source
                    usedNormalizedAnswers += normalizeAnswerValue(answer)
                }
            }
        }

        return MergeOutcome(
            answers = result,
            usedSources = usedSources,
        )
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
        speakerBlocks: List<SpeakerBlock>,
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
        speakerBlocks: List<SpeakerBlock>,
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
        return idNorm.contains("name") ||
                field.normalizedLabel.contains("heiss") ||
                field.normalizedLabel.contains("name")
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

        val splitByQuestionStarter = Regex(
            pattern = """(?i)\b(was|wie|warum|wieso|weshalb|welche|welcher|wer)\b[^.?!]{0,120}$"""
        ).find(withoutSpeakerPrefix)

        val cut = splitByQuestionStarter?.range?.first ?: withoutSpeakerPrefix.length
        return withoutSpeakerPrefix
            .substring(0, cut)
            .trim()
            .trimEnd('.', ';', ',')
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
            if (
                compact.length <= 40 &&
                !compact.contains('?') &&
                !compact.contains('.') &&
                compact.split(Regex("\\s+")).size <= 3
            ) {
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
        return lower.startsWith("was ") ||
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

        val labelOverlap = field.keywords.count { token ->
            token.isNotBlank() && normalized.contains(token)
        }

        return labelOverlap >= 3 && normalized.length <= field.normalizedLabel.length + 16
    }

    private fun extractNameFromText(text: String): String? {
        if (text.isBlank()) return null

        val patterns = listOf(
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

    private fun resolveLlmEngine(): OnDeviceLlmEngine? {
        activeLlmEngine?.let { return it }
        synchronized(llmEngineLock) {
            activeLlmEngine?.let { return it }
            val created = createDefaultOnDeviceLlmEngine()
            if (created != null) {
                activeLlmEngine = created
                println("DEBUG OnDeviceLlmFormMappingRepository: lazily created On-Device-LLM engine")
            }
            return created
        }
    }

    private companion object {
        private const val SMALL_FORM_SINGLE_PASS_THRESHOLD = 4
        private const val MAX_TRANSCRIPT_CHARS_PER_CHUNK = 240
    }
}
