package com.example.bachelor_ai_project.features.transcription.data

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import com.example.bachelor_ai_project.core.result.AppResult
import com.example.bachelor_ai_project.core.result.runCatchingResult
import com.example.bachelor_ai_project.features.transcription.domain.TranscriptSegment
import com.example.bachelor_ai_project.features.transcription.domain.TranscriptionRepository
import com.example.bachelor_ai_project.features.transcription.domain.TranscriptionResponse
import com.whispercpp.whisper.WhisperContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.regex.Pattern
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Lokale Transkription auf Android via whisper.cpp.
 */
class OnDeviceWhisperTranscriptionRepository(
    private val modelPath: String,
) : TranscriptionRepository {

    override suspend fun transcribe(audioFilePath: String): AppResult<TranscriptionResponse> =
        withContext(Dispatchers.Default) {
            runCatchingResult {
                val startedAt = System.currentTimeMillis()
                println("DEBUG OnDeviceWhisperTranscriptionRepository: start thread=${Thread.currentThread().name}")
                val modelFile = File(modelPath)
                require(modelFile.exists() && modelFile.isFile && modelFile.canRead()) {
                    "Whisper model file nicht lesbar: $modelPath"
                }

                val audioFile = File(audioFilePath)
                require(audioFile.exists() && audioFile.isFile && audioFile.canRead()) {
                    "Audio-Datei nicht lesbar: $audioFilePath"
                }

                val samples = decodeAudioToMono16kFloatArray(audioFile)
                require(samples.isNotEmpty()) { "Audio-Datei enthaelt keine Samples" }

                val context = WhisperContext.createContextFromFile(modelPath)
                try {
                    val raw = context.transcribeData(samples, printTimestamp = true).trim()
                    val segments = parseWhisperSegments(raw)
                    val plainText = if (segments.isNotEmpty()) {
                        segments.joinToString(" ") { it.text.trim() }.trim()
                    } else {
                        raw
                    }
                    val durationMs = System.currentTimeMillis() - startedAt
                    println("DEBUG OnDeviceWhisperTranscriptionRepository: done in ${durationMs}ms")

                    TranscriptionResponse(
                        text = plainText,
                        language = "de",
                        duration = samples.size / 16_000.0,
                        segments = segments,
                        words = emptyList(),
                    )
                } finally {
                    context.release()
                }
            }
        }

    private fun parseWhisperSegments(raw: String): List<TranscriptSegment> {
        if (raw.isBlank()) return emptyList()

        val linePattern = Pattern.compile(
            "^\\[(\\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s*-->\\s*(\\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\]:\\s*(.*)$"
        )

        val segments = mutableListOf<TranscriptSegment>()
        raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEachIndexed { index, line ->
                val matcher = linePattern.matcher(line)
                if (!matcher.matches()) {
                    segments += TranscriptSegment(
                        id = index,
                        text = line,
                        speaker = "SPEAKER_00",
                    )
                    return@forEachIndexed
                }

                val startSeconds = parseTimestampSeconds(matcher.group(1) ?: "00:00:00.000")
                val endSeconds = parseTimestampSeconds(matcher.group(2) ?: "00:00:00.000")
                val text = (matcher.group(3) ?: "").trim()
                if (text.isBlank()) return@forEachIndexed

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

private fun decodeAudioToMono16kFloatArray(file: File): FloatArray {
    val bytes = file.readBytes()
    if (bytes.size > 12 && String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF") {
        return decodeWaveToFloatArray(bytes, file.absolutePath)
    }

    val decoded = decodeCompressedAudioToPcm(file.absolutePath)
    val mono = pcm16ToMonoFloat(decoded.pcm16, decoded.channels)
    return resampleLinear(mono, decoded.sampleRate, 16_000)
}

private fun decodeWaveToFloatArray(bytes: ByteArray, pathForError: String): FloatArray {
    require(bytes.size > 44) {
        "Ungueltige WAV-Datei: $pathForError"
    }

    require(String(bytes, 0, 4, Charsets.US_ASCII) == "RIFF") {
        "Nur WAV/RIFF wird fuer On-Device-Transkription unterstuetzt"
    }

    val channels = littleEndianShort(bytes, 22).toInt().coerceAtLeast(1)
    val bitsPerSample = littleEndianShort(bytes, 34).toInt()
    val sampleRate = littleEndianInt(bytes, 24)

    require(bitsPerSample == 16) {
        "Erwartet PCM16 WAV, erhalten: $bitsPerSample bit"
    }

    val dataStart = 44
    val totalSamples = (bytes.size - dataStart) / 2
    val pcm = ShortArray(totalSamples)

    var src = dataStart
    var i = 0
    while (src + 1 < bytes.size && i < totalSamples) {
        pcm[i++] = littleEndianShort(bytes, src)
        src += 2
    }

    val mono = pcm16ToMonoFloat(pcm.copyOf(i), channels)
    return resampleLinear(mono, sampleRate, 16_000)
}

private data class DecodedPcm(
    val pcm16: ShortArray,
    val sampleRate: Int,
    val channels: Int,
)

private fun decodeCompressedAudioToPcm(path: String): DecodedPcm {
    val extractor = MediaExtractor()
    extractor.setDataSource(path)

    var trackIndex = -1
    var format: MediaFormat? = null
    for (index in 0 until extractor.trackCount) {
        val candidate = extractor.getTrackFormat(index)
        val mime = candidate.getString(MediaFormat.KEY_MIME).orEmpty()
        if (mime.startsWith("audio/")) {
            trackIndex = index
            format = candidate
            break
        }
    }

    require(trackIndex >= 0 && format != null) { "Kein Audio-Track gefunden: $path" }
    extractor.selectTrack(trackIndex)

    val inputFormat = checkNotNull(format) { "Audio-Format fehlt: $path" }
    val mime = inputFormat.getString(MediaFormat.KEY_MIME).orEmpty()
    require(mime.isNotBlank()) { "Audio-MIME fehlt: $path" }

    val codec = MediaCodec.createDecoderByType(mime)
    codec.configure(inputFormat, null, null, 0)
    codec.start()

    var outputSampleRate = inputFormat.getIntOrDefault(MediaFormat.KEY_SAMPLE_RATE, 16_000)
    var outputChannels = max(1, inputFormat.getIntOrDefault(MediaFormat.KEY_CHANNEL_COUNT, 1))
    val pcmBytes = ByteArrayOutputStream()
    val info = MediaCodec.BufferInfo()

    var inputEos = false
    var outputEos = false

    try {
        while (!outputEos) {
            if (!inputEos) {
                val inputIndex = codec.dequeueInputBuffer(10_000)
                if (inputIndex >= 0) {
                    val inputBuffer = codec.getInputBuffer(inputIndex)
                    if (inputBuffer != null) {
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                            )
                            inputEos = true
                        } else {
                            val pts = extractor.sampleTime
                            codec.queueInputBuffer(inputIndex, 0, sampleSize, pts, 0)
                            extractor.advance()
                        }
                    }
                }
            }

            val outputIndex = codec.dequeueOutputBuffer(info, 10_000)
            when {
                outputIndex >= 0 -> {
                    val outputBuffer = codec.getOutputBuffer(outputIndex)
                    if (outputBuffer != null && info.size > 0) {
                        val chunk = ByteArray(info.size)
                        outputBuffer.position(info.offset)
                        outputBuffer.limit(info.offset + info.size)
                        outputBuffer.get(chunk)
                        pcmBytes.write(chunk)
                    }
                    codec.releaseOutputBuffer(outputIndex, false)

                    if ((info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputEos = true
                    }
                }
                outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                    val out = codec.outputFormat
                    outputSampleRate = out.getIntOrDefault(MediaFormat.KEY_SAMPLE_RATE, outputSampleRate)
                    outputChannels = max(1, out.getIntOrDefault(MediaFormat.KEY_CHANNEL_COUNT, outputChannels))
                }
            }
        }
    } finally {
        runCatching { codec.stop() }
        runCatching { codec.release() }
        runCatching { extractor.release() }
    }

    val pcmByteArray = pcmBytes.toByteArray()
    require(pcmByteArray.isNotEmpty()) { "Audio-Dekodierung ergab keine PCM-Daten" }

    val sampleCount = pcmByteArray.size / 2
    val pcm16 = ShortArray(sampleCount)
    var index = 0
    var offset = 0
    while (offset + 1 < pcmByteArray.size && index < sampleCount) {
        pcm16[index++] = littleEndianShort(pcmByteArray, offset)
        offset += 2
    }

    return DecodedPcm(
        pcm16 = pcm16.copyOf(index),
        sampleRate = outputSampleRate,
        channels = outputChannels,
    )
}

private fun pcm16ToMonoFloat(pcm16: ShortArray, channels: Int): FloatArray {
    if (pcm16.isEmpty()) return floatArrayOf()
    if (channels <= 1) {
        return FloatArray(pcm16.size) { idx ->
            (pcm16[idx] / 32767.0f).coerceIn(-1.0f, 1.0f)
        }
    }

    val monoCount = pcm16.size / channels
    return FloatArray(monoCount) { frame ->
        var sum = 0f
        for (c in 0 until channels) {
            sum += pcm16[frame * channels + c] / 32767.0f
        }
        (sum / channels).coerceIn(-1.0f, 1.0f)
    }
}

private fun resampleLinear(input: FloatArray, inputRate: Int, targetRate: Int): FloatArray {
    if (input.isEmpty()) return input
    if (inputRate <= 0 || inputRate == targetRate) return input

    val ratio = targetRate.toDouble() / inputRate.toDouble()
    val outputSize = max(1, (input.size * ratio).toInt())
    val output = FloatArray(outputSize)

    for (i in 0 until outputSize) {
        val srcPos = i / ratio
        val left = floor(srcPos).toInt()
        val right = min(left + 1, input.lastIndex)
        val frac = (srcPos - left).toFloat()
        val leftValue = input[left]
        val rightValue = input[right]
        output[i] = leftValue + (rightValue - leftValue) * frac
    }
    return output
}

private fun littleEndianShort(bytes: ByteArray, offset: Int): Short {
    val lo = bytes[offset].toInt() and 0xFF
    val hi = bytes[offset + 1].toInt() shl 8
    return (hi or lo).toShort()
}

private fun MediaFormat.getIntOrDefault(key: String, defaultValue: Int): Int {
    return if (containsKey(key)) getInteger(key) else defaultValue
}

private fun littleEndianInt(bytes: ByteArray, offset: Int): Int {
    val b0 = bytes[offset].toInt() and 0xFF
    val b1 = (bytes[offset + 1].toInt() and 0xFF) shl 8
    val b2 = (bytes[offset + 2].toInt() and 0xFF) shl 16
    val b3 = (bytes[offset + 3].toInt() and 0xFF) shl 24
    return b0 or b1 or b2 or b3
}





