package com.example.bachelor_ai_project.features.form.data

import com.arm.aichat.direct.DirectLlamaBridge
import com.example.bachelor_ai_project.core.result.AppResult
import com.example.bachelor_ai_project.core.result.runCatchingResult
import com.example.bachelor_ai_project.features.recording.domain.AppContextHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * On-Device-Engine ueber direkten llama.cpp JNI-Pfad (ohne AiChat-Flow).
 */
class DirectLlamaOnDeviceLlmEngine(
    private val modelPath: String,
    private val predictLength: Int = 64,
    private val inferenceTimeoutMs: Long = 30_000L,
    private val nCtx: Int = 512,
    private val temperature: Float = 0f,
    private val threadsMin: Int = 2,
    private val threadsMax: Int = 4,
) : WarmupCapableOnDeviceLlmEngine {

    private val mutex = Mutex()
    private val appContext by lazy { AppContextHolder.applicationContext }
    private val bridge by lazy { DirectLlamaBridge.getInstance(appContext) }

    @Volatile
    private var modelLoaded = false
    @Volatile
    private var modelLoading = false
    @Volatile
    private var resolvedModelPath: String? = null

    override fun isModelLoaded(): Boolean = modelLoaded

    override fun isModelLoading(): Boolean = modelLoading

    override suspend fun warmupModel(): AppResult<Unit> = withContext(Dispatchers.Default) {
        runCatchingResult {
            ensureModelLoaded()
            Unit
        }
    }

    override suspend fun completeJson(systemPrompt: String, userPrompt: String): String =
        withContext(Dispatchers.Default) {
            ensureModelLoaded()

            val startedAt = System.currentTimeMillis()
            println("DEBUG DirectLlamaOnDeviceLlmEngine: start inference")

            val raw = bridge.inferJson(
                systemPrompt = systemPrompt,
                userPrompt = userPrompt,
                predictLength = predictLength,
                timeoutMs = inferenceTimeoutMs,
            )

            val durationMs = System.currentTimeMillis() - startedAt
            println("DEBUG DirectLlamaOnDeviceLlmEngine: done in ${durationMs}ms")

            if (raw.startsWith(DIRECT_ERROR_PREFIX)) {
                val reason = raw.removePrefix(DIRECT_ERROR_PREFIX).trim()
                if (reason.contains("timeout", ignoreCase = true)) {
                    recoverAfterFailure("timeout")
                }
                throw IllegalStateException("Direct llama inference failed: $reason")
            }

            raw
        }

    private suspend fun ensureModelLoaded() {
        if (modelLoaded) return

        mutex.withLock {
            if (modelLoaded) return
            modelLoading = true
            try {
                val effectiveModelPath = resolveModelPathForNativeAccess()
                ensureModelFileReadable(effectiveModelPath)

                bridge.ensureInitialized()
                bridge.loadModel(
                    modelPath = effectiveModelPath,
                    nCtx = nCtx,
                    temperature = temperature,
                    threadsMin = threadsMin,
                    threadsMax = threadsMax,
                )

                resolvedModelPath = effectiveModelPath
                modelLoaded = true
                println("DEBUG DirectLlamaOnDeviceLlmEngine: model ready at $effectiveModelPath")
            } finally {
                modelLoading = false
            }
        }
    }

    private fun resolveModelPathForNativeAccess(): String {
        val configured = File(modelPath)
        require(configured.exists()) { "Llama model file not found: $modelPath" }
        require(configured.isFile) { "Llama model path is not a file: $modelPath" }
        require(configured.canRead()) { "Llama model is not readable: $modelPath" }

        val filesRoot = appContext.filesDir.absolutePath
        if (configured.absolutePath.startsWith(filesRoot)) {
            return configured.absolutePath
        }

        val targetDir = File(appContext.filesDir, "models").apply { mkdirs() }
        val targetFile = File(targetDir, configured.name.ifBlank { "model.gguf" })
        if (!targetFile.exists() || targetFile.length() != configured.length()) {
            configured.inputStream().use { input ->
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
        }

        return targetFile.absolutePath
    }

    private fun ensureModelFileReadable(path: String) {
        val file = File(path)
        require(file.exists()) { "Llama model file not found: $path" }
        require(file.isFile) { "Llama model path is not a file: $path" }
        require(file.canRead()) { "Llama model is not readable: $path" }
    }

    private suspend fun recoverAfterFailure(reason: String) {
        mutex.withLock {
            println("DEBUG DirectLlamaOnDeviceLlmEngine: recovery reason=$reason")
            modelLoaded = false
            modelLoading = false
            resolvedModelPath = null
            runCatching { bridge.cancel() }
            runCatching { bridge.unload() }
            runCatching { bridge.shutdown() }
        }
    }

    private companion object {
        private const val DIRECT_ERROR_PREFIX = "__DIRECT_LLM_ERROR__:"
    }
}

