package com.example.bachelor_ai_project.features.form.data

import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import com.arm.aichat.isModelLoaded
import com.example.bachelor_ai_project.features.recording.domain.AppContextHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import kotlin.getValue

/**
 * Adapter, der llama.cpp (AiChat JNI wrapper) auf [OnDeviceLlmEngine] mappt.
 */
class LlamaCppOnDeviceLlmEngine(
    private val modelPath: String,
    private val predictLength: Int = 512,
    private val completionTimeoutMs: Long = 45_000L,
) : OnDeviceLlmEngine {

    companion object {
        private const val MAX_OUTPUT_CHARS = 8_000
    }

    private class JsonGenerationFinished(val json: String) : RuntimeException()

    private val mutex = Mutex()

    private val engine: InferenceEngine by lazy {
        AiChat.getInferenceEngine(AppContextHolder.applicationContext)
    }

    override suspend fun completeJson(systemPrompt: String, userPrompt: String): String =
        withContext(Dispatchers.Default) {
            mutex.withLock {
                val startedAt = System.currentTimeMillis()
                println("DEBUG LlamaCppOnDeviceLlmEngine: start thread=${Thread.currentThread().name}")
                ensureModelFileReadable()
                prepareFreshSession(systemPrompt)

                val buffer = StringBuilder(MAX_OUTPUT_CHARS.coerceAtLeast(512))
                val completed = withTimeoutOrNull(completionTimeoutMs) {
                    try {
                        engine.sendUserPrompt(userPrompt, predictLength).collect { token ->
                            if (token.isBlank()) return@collect

                            if (buffer.length < MAX_OUTPUT_CHARS) {
                                buffer.append(token)
                            }

                            val snapshot = buffer.toString()
                            val jsonCandidate = extractFirstBalancedJson(snapshot)
                            if (!jsonCandidate.isNullOrBlank()) {
                                throw JsonGenerationFinished(jsonCandidate)
                            }

                            if (buffer.length >= MAX_OUTPUT_CHARS) {
                                throw JsonGenerationFinished(snapshot)
                            }
                        }
                    } catch (finished: JsonGenerationFinished) {
                        return@withTimeoutOrNull finished.json
                    }
                    buffer.toString()
                }

                if (completed == null) {
                    runCatching { engine.cleanUp() }
                        .onFailure { error ->
                            println("DEBUG LlamaCppOnDeviceLlmEngine: cleanup nach Timeout fehlgeschlagen: ${error.message}")
                        }
                    throw IllegalStateException(
                        "Lokale LLM-Inferenz Timeout nach ${completionTimeoutMs / 1000}s"
                    )
                }

                val durationMs = System.currentTimeMillis() - startedAt
                println("DEBUG LlamaCppOnDeviceLlmEngine: done in ${durationMs}ms")
                completed
            }
        }

    private suspend fun prepareFreshSession(systemPrompt: String) {
        val state = engine.state.value
        if (!state.isModelLoaded) {
            val loadStartedAt = System.currentTimeMillis()
            engine.loadModel(modelPath)
            val loadDurationMs = System.currentTimeMillis() - loadStartedAt
            println("DEBUG LlamaCppOnDeviceLlmEngine: model load done in ${loadDurationMs}ms")
        }

        engine.setSystemPrompt(systemPrompt)
    }

    private fun ensureModelFileReadable() {
        val file = File(modelPath)
        require(file.exists()) { "Llama model file not found: $modelPath" }
        require(file.isFile) { "Llama model path is not a file: $modelPath" }
        require(file.canRead()) { "Llama model is not readable: $modelPath" }
    }

    private fun extractFirstBalancedJson(text: String): String? {
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
                if (depth == 0 && start >= 0) {
                    return text.substring(start, index + 1)
                }
            }
        }

        return null
    }
}

