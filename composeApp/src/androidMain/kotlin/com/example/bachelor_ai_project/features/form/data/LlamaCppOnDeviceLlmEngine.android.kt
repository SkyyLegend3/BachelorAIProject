package com.example.bachelor_ai_project.features.form.data

import com.arm.aichat.AiChat
import com.arm.aichat.InferenceEngine
import com.arm.aichat.isModelLoaded
import com.example.bachelor_ai_project.core.result.AppResult
import com.example.bachelor_ai_project.core.result.runCatchingResult
import com.example.bachelor_ai_project.features.recording.domain.AppContextHolder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import kotlin.getValue

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
    @Volatile
    private var modelLoaded: Boolean = false
    @Volatile
    private var modelLoading: Boolean = false

    private val engine: InferenceEngine by lazy {
        AiChat.getInferenceEngine(AppContextHolder.applicationContext)
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
            mutex.withLock {
                val startedAt = System.currentTimeMillis()
                println("DEBUG LlamaCppOnDeviceLlmEngine: start thread=${Thread.currentThread().name}")
                ensureModelFileReadable()
                engine.setSystemPrompt(systemPrompt)

                val buffer = StringBuilder()
                var tokenCount = 0
                var firstTokenLogged = false
                try {
                    engine.sendUserPrompt(userPrompt, predictLength).collect { token ->
                        if (!firstTokenLogged) {
                            firstTokenLogged = true
                            val firstTokenAfterMs = System.currentTimeMillis() - startedAt
                            println("DEBUG LlamaCppOnDeviceLlmEngine: first token after ${firstTokenAfterMs}ms")
                        }
                        tokenCount += 1
                        buffer.append(token)
                    }
                } catch (error: Throwable) {
                    val elapsedMs = System.currentTimeMillis() - startedAt
                    println(
                        "DEBUG LlamaCppOnDeviceLlmEngine: inference failed after ${elapsedMs}ms, " +
                            "tokens=${tokenCount}, message=${error.message}"
                    )
                    throw error
                }
                val durationMs = System.currentTimeMillis() - startedAt
                println("DEBUG LlamaCppOnDeviceLlmEngine: done in ${durationMs}ms, tokens=${tokenCount}")
                buffer.toString()
            }
        }

    private suspend fun ensureModelLoaded() {
        if (modelLoaded) return

        mutex.withLock {
            if (modelLoaded) return
            modelLoading = true
            try {
                ensureModelFileReadable()
                val state = engine.state.value
                if (!state.isModelLoaded) {
                    engine.configureRuntime(
                        nCtx = nCtx,
                        temperature = temperature,
                        threadsMin = threadsMin,
                        threadsMax = threadsMax,
                    )
                    println("DEBUG LlamaCppOnDeviceLlmEngine: lade Modell einmalig: $modelPath")
                    engine.loadModel(modelPath)
                }
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
    ): T = withContext(Dispatchers.IO) {
        val executor = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "llama-$label-worker").apply {
                isDaemon = true
                priority = Thread.NORM_PRIORITY - 1
            }
        }

        val future = executor.submit<T> {
            runBlocking { block() }
        }

        try {
            future.get(timeoutMs, TimeUnit.MILLISECONDS)
        } catch (_: TimeoutException) {
            future.cancel(true)
            throw IllegalStateException("Llama $label timeout nach ${timeoutMs}ms")
        } finally {
            executor.shutdownNow()
        }
    }

    private fun ensureModelFileReadable() {
        val file = File(modelPath)
        require(file.exists()) { "Llama model file not found: $modelPath" }
        require(file.isFile) { "Llama model path is not a file: $modelPath" }
        require(file.canRead()) { "Llama model is not readable: $modelPath" }
    }
}

