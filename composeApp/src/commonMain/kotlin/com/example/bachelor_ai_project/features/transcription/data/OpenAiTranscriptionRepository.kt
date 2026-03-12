package com.example.bachelor_ai_project.features.transcription.data

import com.example.bachelor_ai_project.core.result.AppResult
import com.example.bachelor_ai_project.core.result.runCatchingResult
import com.example.bachelor_ai_project.features.transcription.domain.TranscriptionRepository
import com.example.bachelor_ai_project.features.transcription.domain.TranscriptionResponse
import com.example.bachelor_ai_project.features.transcription.domain.readFileBytes
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import kotlinx.serialization.json.Json

private const val TRANSCRIPTION_URL = "https://api.openai.com/v1/audio/transcriptions"

/**
 * Implementierung von [TranscriptionRepository] via OpenAI Transcriptions-API.
 *
 * Verwendet whisper-1 mit verbose_json, da dies das einzige Modell ist,
 * das zuverlässig Segmente mit Zeitstempeln zurückgibt.
 *
 * @param apiKey   OpenAI API-Key (sk-...)
 * @param httpClient Gemeinsamer HttpClient aus HttpClientProvider
 */
class OpenAiTranscriptionRepository(
    private val apiKey: String,
    private val httpClient: HttpClient,
) : TranscriptionRepository {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override suspend fun transcribe(audioFilePath: String): AppResult<TranscriptionResponse> =
        runCatchingResult {
            val audioBytes = readFileBytes(audioFilePath)
            val rawResponse: HttpResponse = httpClient.post(TRANSCRIPTION_URL) {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append(
                                key = "file",
                                value = audioBytes,
                                headers = Headers.build {
                                    append(HttpHeaders.ContentType, "audio/mp4")
                                    append(HttpHeaders.ContentDisposition, "filename=\"recording.m4a\"")
                                }
                            )
                            // whisper-1: einziges Modell mit stabiler verbose_json + Segment-Unterstützung
                            append("model", "whisper-1")
                            append("response_format", "verbose_json")
                            append("language", "de")
                        }
                    )
                )
            }
            val bodyText = rawResponse.body<String>()
            println("DEBUG TranscriptionResponse raw: $bodyText")
            json.decodeFromString<TranscriptionResponse>(bodyText)
        }
}

