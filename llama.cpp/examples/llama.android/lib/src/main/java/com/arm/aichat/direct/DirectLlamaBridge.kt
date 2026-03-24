package com.arm.aichat.direct

import android.content.Context
import java.io.File

/**
 * Direkter JNI-Zugriff auf llama.cpp ohne Flow-basierte Token-Bridge.
 */
class DirectLlamaBridge private constructor(
    context: Context,
) {
    private val nativeLibDir: String = context.applicationInfo.nativeLibraryDir

    companion object {
        @Volatile
        private var instance: DirectLlamaBridge? = null

        fun getInstance(context: Context): DirectLlamaBridge {
            instance?.let { return it }
            return synchronized(this) {
                instance ?: DirectLlamaBridge(context.applicationContext).also { instance = it }
            }
        }
    }

    @Volatile
    private var initialized: Boolean = false

    private external fun nativeInit(nativeLibDir: String): Int
    private external fun nativeLoadModel(
        modelPath: String,
        nCtx: Int,
        temperature: Float,
        threadsMin: Int,
        threadsMax: Int,
    ): Int

    private external fun nativeInfer(
        systemPrompt: String,
        userPrompt: String,
        predictLength: Int,
        timeoutMs: Long,
    ): String

    private external fun nativeCancel()
    private external fun nativeUnload()
    private external fun nativeShutdown()

    fun ensureInitialized() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            System.loadLibrary("ai-chat")
            val result = nativeInit(nativeLibDir)
            check(result == 0) { "Native init failed: $result" }
            initialized = true
        }
    }

    fun loadModel(
        modelPath: String,
        nCtx: Int,
        temperature: Float,
        threadsMin: Int,
        threadsMax: Int,
    ) {
        ensureInitialized()
        val file = File(modelPath)
        require(file.exists() && file.isFile && file.canRead()) { "Model invalid: $modelPath" }

        val result = nativeLoadModel(modelPath, nCtx, temperature, threadsMin, threadsMax)
        check(result == 0) { nativeLoadErrorMessage(result, modelPath) }
    }

    fun inferJson(
        systemPrompt: String,
        userPrompt: String,
        predictLength: Int,
        timeoutMs: Long,
    ): String {
        ensureInitialized()
        return nativeInfer(systemPrompt, userPrompt, predictLength, timeoutMs)
    }

    fun cancel() {
        if (!initialized) return
        nativeCancel()
    }

    fun unload() {
        if (!initialized) return
        nativeUnload()
    }

    fun shutdown() {
        if (!initialized) return
        nativeShutdown()
        initialized = false
    }

    private fun nativeLoadErrorMessage(result: Int, modelPath: String): String = when (result) {
        1 -> "LLM model could not be loaded from file: $modelPath"
        2 -> "LLM context initialization failed. Reduce LLAMA_N_CTX or use a smaller model."
        3 -> "LLM chat template initialization failed for model: $modelPath"
        4 -> "LLM sampler initialization failed for model: $modelPath"
        else -> "Native loadModel failed with code $result for model: $modelPath"
    }
}
