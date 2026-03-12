package com.example.bachelor_ai_project.features.form.data

import com.example.bachelor_ai_project.core.result.AppResult
import com.example.bachelor_ai_project.core.result.runCatchingResult
import com.example.bachelor_ai_project.features.form.domain.FormDefinitionProvider
import com.example.bachelor_ai_project.features.form.domain.FormMappingRepository
import com.example.bachelor_ai_project.features.form.domain.LlmFieldMappingResponse
import com.example.bachelor_ai_project.features.form.domain.TranscriptMappingResult
import com.example.bachelor_ai_project.features.form.domain.TranscriptToFormMapper
import com.example.bachelor_ai_project.features.transcription.domain.TranscriptionResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject

private const val CHAT_COMPLETIONS_URL = "https://api.openai.com/v1/chat/completions"
private const val MODEL = "gpt-4o-mini"

/**
 * Implementierung von [FormMappingRepository] mit GPT-4o-mini-Anbindung.
 *
 * Ablauf:
 * 1. Strukturelles Mapping: Transkript → [SpeakerBlock]s (via [TranscriptToFormMapper])
 * 2. Prompt-Aufbau: Sprecher-Blöcke + Formularfragen werden als strukturierter
 *    System-Prompt an GPT-4o-mini gesendet
 * 3. Das Modell antwortet mit einem JSON-Objekt `{ "answers": { "questionId": "text", … } }`
 * 4. Felder ohne passenden Transkript-Inhalt liefert das Modell als `""` zurück –
 *    diese werden gefiltert und **nicht** in das Formular übernommen
 *
 * @param apiKey             OpenAI API-Key (sk-...)
 * @param httpClient         Gemeinsamer Ktor-HttpClient
 * @param definitionProvider Liefert die aktuellen Formularfragen (für den Prompt)
 */
class AiFormMappingRepository(
    private val apiKey: String,
    private val httpClient: HttpClient,
    private val definitionProvider: FormDefinitionProvider,
) : FormMappingRepository {

    private val mapper = TranscriptToFormMapper()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun mapTranscript(
        response: TranscriptionResponse,
    ): AppResult<TranscriptMappingResult> = runCatchingResult {

        // 1. Sprecher-Blöcke strukturell aufbauen
        val speakerBlocks = mapper.map(response).speakerBlocks

        // 2. Transkript als lesbaren Text für den Prompt aufbereiten
        // Fallback: wenn keine Segmente vorhanden, den rohen text der Response nutzen
        val transcriptText = if (speakerBlocks.isNotEmpty()) {
            speakerBlocks.joinToString("\n") { block ->
                "[${block.speaker.ifBlank { "Sprecher" }}]: ${block.text}"
            }
        } else if (response.text.isNotBlank()) {
            println("DEBUG AiFormMappingRepository: Keine Segmente – nutze response.text als Fallback")
            response.text
        } else {
            error("Transkript ist leer – kein Text vorhanden zum Mappen")
        }

        println("DEBUG AiFormMappingRepository transcriptText: $transcriptText")

        // 3. Formularfelder als JSON-Schema für den Prompt beschreiben
        val fieldsDescription = definitionProvider.questions.joinToString("\n") { q ->
            "- \"${q.id}\": ${q.label}"
        }

        val systemPrompt = """
            Du bist ein Assistent, der Informationen aus einem Gesprächstranskript
            in strukturierte Formularfelder extrahiert. Das Gespräch ist ein Feedbackgespräch
            zwischen zwei Personen.
            
            Antworte AUSSCHLIESSLICH mit einem validen JSON-Objekt in GENAU diesem Format,
            ohne Markdown, ohne Code-Blöcke, ohne Erklärungen:
            {"answers": {"feldId": "extrahierter Text"}}
            
            Regeln:
            - Übernehme nur Informationen, die explizit im Transkript stehen.
            - Erfinde oder ergänze NICHTS.
            - Felder ohne passende Information WEGLASSEN (nicht als leeren String eintragen).
            - Antworte auf Deutsch.
            - Nur das JSON-Objekt zurückgeben, KEIN weiterer Text.
            
            Zu befüllende Formularfelder:
            $fieldsDescription
        """.trimIndent()

        val userMessage = """
            Transkript:
            $transcriptText
        """.trimIndent()

        // 4. Chat-Completion-Request aufbauen
        val requestBody = buildJsonObject {
            put("model", MODEL)
            putJsonArray("messages") {
                add(buildJsonObject {
                    put("role", "system")
                    put("content", systemPrompt)
                })
                add(buildJsonObject {
                    put("role", "user")
                    put("content", userMessage)
                })
            }
            putJsonObject("response_format") {
                put("type", "json_object")
            }
            put("temperature", 0.0)
        }

        // 5. Request senden
        val rawResponse = httpClient.post(CHAT_COMPLETIONS_URL) {
            header(HttpHeaders.Authorization, "Bearer $apiKey")
            contentType(ContentType.Application.Json)
            setBody(requestBody)
        }.body<JsonObject>()

        // 6. Antwort-Text aus choices[0].message.content extrahieren
        val content = (rawResponse["choices"] as? JsonArray)
            ?.firstOrNull()
            ?.let { it as? JsonObject }
            ?.get("message")
            ?.let { it as? JsonObject }
            ?.get("content")
            ?.let { it as? JsonPrimitive }
            ?.content
            ?: error("Ungültige GPT-Antwortstruktur")

        println("DEBUG AiFormMappingRepository GPT content: $content")

        // 7. JSON parsen und leere Felder herausfiltern
        val llmResponse = json.decodeFromString<LlmFieldMappingResponse>(content)
        println("DEBUG AiFormMappingRepository fieldAnswers: ${llmResponse.answers}")
        val filteredAnswers = llmResponse.answers.filter { (_, value) -> value.isNotBlank() }

        TranscriptMappingResult(
            speakerBlocks = speakerBlocks,
            fieldAnswers = filteredAnswers,
        )
    }
}

