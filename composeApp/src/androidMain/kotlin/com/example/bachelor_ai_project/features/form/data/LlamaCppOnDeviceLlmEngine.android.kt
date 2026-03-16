package com.example.bachelor_ai_project.features.form.data

import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import com.arm.aichat.isModelLoaded
import com.example.bachelor_ai_project.features.recording.domain.AppContextHolder
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * Adapter, der llama.cpp (AiChat JNI wrapper) auf [OnDeviceLlmEngine] mappt.
 */
class LlamaCppOnDeviceLlmEngine(
    private val modelPath: String,
    private val predictLength: Int = 1024,
) : OnDeviceLlmEngine {

    private val mutex = Mutex()

    private val engine: InferenceEngine by lazy {
        AiChat.getInferenceEngine(AppContextHolder.applicationContext)
    }

    override suspend fun completeJson(systemPrompt: String, userPrompt: String): String = mutex.withLock {
        ensureModelFileReadable()
        prepareFreshSession(systemPrompt)

        val buffer = StringBuilder()
        engine.sendUserPrompt(userPrompt, predictLength).collect { token ->
            buffer.append(token)
        }
        buffer.toString()
    }

    private suspend fun prepareFreshSession(systemPrompt: String) {
        val state = engine.state.value
        if (state.isModelLoaded) {
            runCatching { engine.cleanUp() }
                .onFailure { error ->
                    println("DEBUG LlamaCppOnDeviceLlmEngine: cleanup fehlgeschlagen: ${error.message}")
                }
        }

        engine.loadModel(modelPath)
        engine.setSystemPrompt(systemPrompt)
    }

    private fun ensureModelFileReadable() {
        val file = File(modelPath)
        require(file.exists()) { "Llama model file not found: $modelPath" }
        require(file.isFile) { "Llama model path is not a file: $modelPath" }
        require(file.canRead()) { "Llama model is not readable: $modelPath" }
    }
}

