package com.example.bachelor_ai_project.features.form.data

import android.app.ActivityManager
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
                enforceLikelyMemoryBudget(effectiveModelPath)

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
        val resolvedPath = AndroidLlamaModelPathResolver.resolveExistingModelPath(modelPath) ?: modelPath
        val configured = File(resolvedPath)
        require(configured.exists()) { "Llama model file not found: $resolvedPath" }
        require(configured.isFile) { "Llama model path is not a file: $modelPath" }
        require(configured.canRead()) { "Llama model is not readable: $resolvedPath" }

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

    private fun enforceLikelyMemoryBudget(path: String) {
        val file = File(path)
        val modelBytes = file.length().coerceAtLeast(1L)
        val activityManager = appContext.getSystemService(ActivityManager::class.java) ?: return
        val memInfo = ActivityManager.MemoryInfo().also(activityManager::getMemoryInfo)

        val estimatedNativeBytes = estimateNativeFootprintBytes(modelBytes)
        val allowedByTotalMem = (memInfo.totalMem * MAX_MODEL_FOOTPRINT_SHARE_OF_TOTAL_MEM).toLong()
        val rejectByTotalMem = memInfo.totalMem > 0 && estimatedNativeBytes > allowedByTotalMem
        val rejectByLowMemState = memInfo.lowMemory && estimatedNativeBytes > memInfo.availMem

        if (rejectByTotalMem || rejectByLowMemState) {
            val message = buildString {
                append("LLM model likely too large for this device runtime: ")
                append("model=")
                append(formatGiB(modelBytes))
                append(" GiB, estimated_native_peak=")
                append(formatGiB(estimatedNativeBytes))
                append(" GiB, total_mem=")
                append(formatGiB(memInfo.totalMem))
                append(" GiB, avail_mem=")
                append(formatGiB(memInfo.availMem))
                append(" GiB. Use a smaller GGUF (z. B. Q2/Q3) on this device.")
            }
            throw IllegalStateException(message)
        }
    }

    private fun estimateNativeFootprintBytes(modelBytes: Long): Long {
        // Faustregel fuer mobile llama.cpp-Loads: mmap + optionale interne Repack-/Workspace-Peaks + Reserve.
        val modelPlusPeakFactor = (modelBytes * 17L) / 10L // ~1.7x
        return modelPlusPeakFactor + EXTRA_RUNTIME_HEADROOM_BYTES
    }

    private fun formatGiB(bytes: Long): String = "%.2f".format(bytes.toDouble() / BYTES_PER_GIB)

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
        private const val BYTES_PER_GIB = 1024.0 * 1024.0 * 1024.0
        private const val MAX_MODEL_FOOTPRINT_SHARE_OF_TOTAL_MEM = 0.75
        private const val EXTRA_RUNTIME_HEADROOM_BYTES = 384L * 1024L * 1024L
    }
}
