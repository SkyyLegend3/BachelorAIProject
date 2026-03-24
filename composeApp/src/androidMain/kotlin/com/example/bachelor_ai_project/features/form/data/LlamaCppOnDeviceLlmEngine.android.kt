package com.example.bachelor_ai_project.features.form.data

import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import com.arm.aichat.isModelLoaded
import com.example.bachelor_ai_project.core.result.AppResult
import com.example.bachelor_ai_project.core.result.runCatchingResult
import com.example.bachelor_ai_project.features.recording.domain.AppContextHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import java.io.File
import kotlin.getValue
import kotlinx.coroutines.flow.first

/**
 * Adapter, der llama.cpp (AiChat JNI wrapper) auf [OnDeviceLlmEngine] mappt.
 */
class LlamaCppOnDeviceLlmEngine(
    private val modelPath: String,
    private val predictLength: Int = 256,
    private val inferenceTimeoutMs: Long = 60_000L,
    private val nCtx: Int = 512,
    private val temperature: Float = 0f,
    private val threadsMin: Int = 2,
    private val threadsMax: Int = 4,
) : WarmupCapableOnDeviceLlmEngine {

    private val mutex = Mutex()
    private val appContext by lazy { AppContextHolder.applicationContext }
    @Volatile
    private var modelLoaded: Boolean = false
    @Volatile
    private var modelLoading: Boolean = false
    @Volatile
    private var resolvedModelPath: String? = null

    @Volatile
    private var engineRef: InferenceEngine? = null

    private fun engine(): InferenceEngine {
        println("DEBUG EngineProvider: falling back to LlamaCppOnDeviceLlmEngine")
        engineRef?.let { return it }
        return synchronized(this) {
            engineRef ?: AiChat.getInferenceEngine(appContext).also { engineRef = it }
        }
    }

    override fun isModelLoaded(): Boolean = modelLoaded

    override fun isModelLoading(): Boolean = modelLoading

    override suspend fun warmupModel(): AppResult<Unit> = withContext(Dispatchers.Default) {
        runCatchingResult {
            runEngineCallWithTimeout(label = "warmup") {
                ensureModelLoaded()
            }
            Unit
        }
    }

    override suspend fun completeJson(systemPrompt: String, userPrompt: String): String =
        runEngineCallWithTimeout(label = "inference") {
            ensureModelLoaded()
            ensureModelFileReadable()

            val startedAt = System.currentTimeMillis()
            println("DEBUG completeJson: start thread=${Thread.currentThread().name}")

            val engine = engine()
            val buffer = StringBuilder()
            var tokenCount = 0
            var firstTokenLogged = false

            mutex.withLock {
                println("DEBUG completeJson: before setSystemPrompt")
                println("DEBUG completeJson: systemPrompt=[$systemPrompt]")
                engine.setSystemPrompt(systemPrompt)
                println("DEBUG completeJson: before sendUserPrompt")
                println("DEBUG completeJson: userPrompt=[$userPrompt]")

                engine.sendUserPrompt(userPrompt, predictLength).collect { token ->
                    if (!firstTokenLogged) {
                        firstTokenLogged = true
                        val firstTokenAfterMs = System.currentTimeMillis() - startedAt
                        println("DEBUG completeJson: first token after ${firstTokenAfterMs}ms")
                    }
                    tokenCount++
                    buffer.append(token)
                }
            }

            println("DEBUG completeJson: raw=[$buffer]")
            buffer.toString()
        }


    private suspend fun ensureModelLoaded() {
        if (modelLoaded) return

        mutex.withLock {
            if (modelLoaded) return
            modelLoading = true
            try {
                val effectiveModelPath = resolveModelPathForNativeAccess()
                ensureModelFileReadable(effectiveModelPath)

                val engine = engine()

                println("DEBUG model effective path=$effectiveModelPath")
                println("DEBUG model size=${File(effectiveModelPath).length()}")
                println("DEBUG state before load=${engine.state.value}")

                if (!engine.state.value.isModelLoaded) {
                    engine.configureRuntime(
                        nCtx = nCtx,
                        temperature = temperature,
                        threadsMin = threadsMin,
                        threadsMax = threadsMax,
                    )

                    println("DEBUG loading model now")
                    engine.loadModel(effectiveModelPath)

                    println("DEBUG waiting for loaded state")
                    engine.state.first { it.isModelLoaded }
                    println("DEBUG loaded state reached=${engine.state.value}")
                } else {
                    println("DEBUG model already loaded, state=${engine.state.value}")
                }

                resolvedModelPath = effectiveModelPath
                modelLoaded = true
            } finally {
                modelLoading = false
            }
        }
    }

    private suspend fun <T> runEngineCallWithTimeout(
        label: String,
        timeoutMs: Long = inferenceTimeoutMs,
        block: suspend () -> T,
    ): T = withContext(Dispatchers.Default) {
        try {
            withTimeout(timeoutMs) {
                block()
            }
        } catch (error: kotlinx.coroutines.TimeoutCancellationException) {
            recoverEngineAfterTimeout(label)
            throw IllegalStateException("Llama $label timeout nach ${timeoutMs}ms", error)
        }
    }

    private fun ensureModelFileReadable() {
        val path = resolvedModelPath ?: resolveModelPathForNativeAccess().also { resolvedModelPath = it }
        ensureModelFileReadable(path)
    }

    private fun ensureModelFileReadable(path: String) {
        val file = File(path)
        require(file.exists()) { "Llama model file not found: $path" }
        require(file.isFile) { "Llama model path is not a file: $path" }
        require(file.canRead()) { "Llama model is not readable: $path" }
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

    private suspend fun recoverEngineAfterTimeout(label: String) {
        println("DEBUG LlamaCppOnDeviceLlmEngine: timeout recovery start for $label")
        mutex.withLock {
            modelLoaded = false
            modelLoading = false
            resolvedModelPath = null

            val current = engineRef
            if (current != null) {
                runCatching { current.cleanUp() }
                runCatching { current.destroy() }
            }
            engineRef = null
        }
        println("DEBUG LlamaCppOnDeviceLlmEngine: timeout recovery done for $label")
    }
}
