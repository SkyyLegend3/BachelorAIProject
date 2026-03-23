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
import io.ktor.http.HttpStatusCode
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
            val fileNameFromPath = audioFilePath.substringAfterLast('/').ifBlank { "audio" }
            val extFromBytes = detectExtensionFromBytes(audioBytes)
            val fileName = buildUploadFileName(fileNameFromPath, extFromBytes)
            val contentType = guessAudioContentType(fileName)

            println(
                "DEBUG UploadAudio: path=$audioFilePath, uploadFileName=$fileName, " +
                    "contentType=$contentType, bytes=${audioBytes.size}, " +
                    "magicHex=${audioBytes.magicHex(32)}, magicAscii=${audioBytes.magicAscii(32)}, " +
                    "ftypBrand=${audioBytes.mp4FtypBrand()}"
            )

            val rawResponse: HttpResponse = httpClient.post(TRANSCRIPTION_URL) {
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append(
                                key = "file",
                                value = audioBytes,
                                headers = Headers.build {
                                    append(HttpHeaders.ContentType, contentType)
                                    append(
                                        HttpHeaders.ContentDisposition,
                                        "form-data; name=\"file\"; filename=\"${fileName.escapeForHeader()}\""
                                    )
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

            if (rawResponse.status != HttpStatusCode.OK) {
                error(
                    "Transkription API Fehler: status=${rawResponse.status.value}, " +
                        "audioBytes=${audioBytes.size}, body=${bodyText.take(500)}"
                )
            }

            val decoded = json.decodeFromString<TranscriptionResponse>(bodyText)
            val hasUsableContent = decoded.text.isNotBlank() || decoded.segments.isNotEmpty() || decoded.words.isNotEmpty()
            if (!hasUsableContent) {
                error(
                    "Leere Transkriptionsantwort trotz HTTP 200: " +
                        "audioBytes=${audioBytes.size}, body=${bodyText.take(500)}"
                )
            }

            decoded
        }

    private fun guessAudioContentType(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            // m4a ist in der Regel MP4-Container mit AAC-Stream
            "m4a" -> "audio/mp4"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "webm" -> "audio/webm"
            "mp4" -> "audio/mp4"
            else -> "application/octet-stream"
        }
    }

    private fun buildUploadFileName(rawName: String, extFromBytes: String?): String {
        val safeBase = rawName.substringBeforeLast('.').ifBlank { "audio" }
        val finalExt = extFromBytes ?: rawName.substringAfterLast('.', missingDelimiterValue = "").lowercase().ifBlank { "m4a" }
        return "$safeBase.$finalExt"
    }

    private fun detectExtensionFromBytes(bytes: ByteArray): String? {
        if (bytes.size < 12) return null

        fun startsWithAscii(prefix: String): Boolean {
            if (bytes.size < prefix.length) return false
            return prefix.indices.all { idx -> bytes[idx].toInt().toChar() == prefix[idx] }
        }

        // FLAC: fLaC
        if (startsWithAscii("fLaC")) return "flac"

        // WAV: RIFF....WAVE
        if (startsWithAscii("RIFF") && bytes.size >= 12) {
            val wave = charArrayOf(
                bytes[8].toInt().toChar(),
                bytes[9].toInt().toChar(),
                bytes[10].toInt().toChar(),
                bytes[11].toInt().toChar()
            ).concatToString()
            if (wave == "WAVE") return "wav"
        }

        // OGG/OGA: OggS
        if (startsWithAscii("OggS")) return "ogg"

        // MP3: ID3 header
        if (startsWithAscii("ID3")) return "mp3"

        // MP4/M4A: ....ftyp
        if (bytes[4].toInt().toChar() == 'f' && bytes[5].toInt().toChar() == 't' && bytes[6].toInt().toChar() == 'y' && bytes[7].toInt().toChar() == 'p') {
            val brand = charArrayOf(
                bytes[8].toInt().toChar(),
                bytes[9].toInt().toChar(),
                bytes[10].toInt().toChar(),
                bytes[11].toInt().toChar()
            ).concatToString().lowercase()

            return when {
                brand.contains("m4a") || brand.contains("m4b") || brand.contains("m4p") -> "m4a"
                else -> "mp4"
            }
        }

        return null
    }

    private fun String.escapeForHeader(): String =
        replace("\\", "_").replace("\"", "_")

    private fun ByteArray.magicHex(limit: Int): String =
        take(limit).joinToString(separator = " ") { byte ->
            (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
        }

    private fun ByteArray.magicAscii(limit: Int): String =
        take(limit).joinToString(separator = "") { byte ->
            val c = (byte.toInt() and 0xFF).toChar()
            if (c.code in 32..126) c.toString() else "."
        }

    private fun ByteArray.mp4FtypBrand(): String {
        if (size < 12) return "n/a"
        val hasFtyp = this[4].toInt().toChar() == 'f' &&
            this[5].toInt().toChar() == 't' &&
            this[6].toInt().toChar() == 'y' &&
            this[7].toInt().toChar() == 'p'
        if (!hasFtyp) return "n/a"
        return charArrayOf(
            this[8].toInt().toChar(),
            this[9].toInt().toChar(),
            this[10].toInt().toChar(),
            this[11].toInt().toChar(),
        ).concatToString()
    }
}

