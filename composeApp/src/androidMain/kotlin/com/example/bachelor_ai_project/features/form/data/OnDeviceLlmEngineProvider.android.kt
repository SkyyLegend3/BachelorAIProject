package com.example.bachelor_ai_project.features.form.data

import com.example.bachelor_ai_project.BuildConfig
import com.example.bachelor_ai_project.core.config.AppConfig

/**
 * Zentrale Stelle fuer die Android-Lokal-LLM-Engine.
 *
 * Liefert eine llama.cpp-Engine, sobald ein Modellpfad konfiguriert ist.
 */
fun createDefaultOnDeviceLlmEngine(): OnDeviceLlmEngine? {
        val modelPath = AppConfig.llamaModelPath.trim()
        if (modelPath.isBlank()) return null

        val configuredPredictLength = BuildConfig.LLAMA_PREDICT_LENGTH.coerceAtLeast(1)
        val predictLength = if (BuildConfig.LLAMA_PERFORMANCE_MODE) {
                configuredPredictLength.coerceAtMost(16)
        } else {
                configuredPredictLength.coerceAtMost(32)
        }
        val inferenceTimeoutMs = BuildConfig.LLAMA_INFERENCE_TIMEOUT_MS.coerceAtLeast(5_000L)
        val nCtx = BuildConfig.LLAMA_N_CTX.coerceAtLeast(128)
        val temperature = BuildConfig.LLAMA_TEMPERATURE.coerceAtLeast(0f)
        val threadsMin = BuildConfig.LLAMA_THREADS_MIN.coerceAtLeast(1)
        val threadsMax = BuildConfig.LLAMA_THREADS_MAX.coerceAtLeast(threadsMin)
        println(
                "DEBUG OnDeviceLlmEngineProvider: performanceMode=${BuildConfig.LLAMA_PERFORMANCE_MODE}, " +
                        "predictLength=$predictLength (configured=$configuredPredictLength), timeoutMs=$inferenceTimeoutMs, " +
                        "nCtx=$nCtx, temperature=$temperature, threads=$threadsMin-$threadsMax"
        )
        if (!isAiChatStubActive()) {
                println("DEBUG EngineProvider: using LlamaCppOnDeviceLlmEngine")
                return LlamaCppOnDeviceLlmEngine(
                        modelPath = modelPath,
                        predictLength = predictLength,
                        inferenceTimeoutMs = inferenceTimeoutMs,
                        nCtx = nCtx,
                        temperature = temperature,
                        threadsMin = threadsMin,
                        threadsMax = threadsMax,
                )
        }

        println("DEBUG OnDeviceLlmEngineProvider: AiChat stub active, fallback to DirectLlamaOnDeviceLlmEngine")
        return DirectLlamaOnDeviceLlmEngine(
                modelPath = modelPath,
                predictLength = predictLength,
                inferenceTimeoutMs = inferenceTimeoutMs,
                nCtx = nCtx,
                temperature = temperature,
                threadsMin = threadsMin,
                threadsMax = threadsMax,
        )
}

private fun isAiChatStubActive(): Boolean = runCatching {
	val cls = Class.forName("com.arm.aichat.AiChat")
	cls.getDeclaredField("IS_STUB").getBoolean(null)
}.getOrDefault(false)
