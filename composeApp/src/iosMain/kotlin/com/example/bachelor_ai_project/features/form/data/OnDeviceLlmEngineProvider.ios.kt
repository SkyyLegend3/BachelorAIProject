package com.example.bachelor_ai_project.features.form.data

import com.example.bachelor_ai_project.core.config.AppConfig

/**
 * Swift-seitige Bridge fuer lokale llama.cpp-Inferenz auf iOS.
 *
 * Die Implementierung wird im iosApp-Target bereitgestellt und beim App-Start registriert.
 */
interface IosLlmBridge {
    fun completeJson(
        modelPath: String,
        systemPrompt: String,
        userPrompt: String,
        maxTokens: Int,
    ): String
}

/**
 * Registry, damit Swift die Bridge einmalig registrieren kann.
 */
object IosLlmBridgeRegistry {
    private var bridge: IosLlmBridge? = null

    @Suppress("unused") // Wird aus Swift aufgerufen.
    fun register(bridge: IosLlmBridge?) {
        this.bridge = bridge
    }

    fun current(): IosLlmBridge? = bridge
}

private class BridgedOnDeviceLlmEngine(
    private val modelPath: String,
    private val bridge: IosLlmBridge,
    private val maxTokens: Int = 1024,
) : OnDeviceLlmEngine {

    override suspend fun completeJson(systemPrompt: String, userPrompt: String): String =
        bridge.completeJson(
            modelPath = modelPath,
            systemPrompt = systemPrompt,
            userPrompt = userPrompt,
            maxTokens = maxTokens,
        )
}

/**
 * Zentrale Stelle fuer die iOS-Lokal-LLM-Engine.
 */
fun createDefaultOnDeviceLlmEngine(): OnDeviceLlmEngine? {
    val modelPath = AppConfig.llamaModelPath.trim()

    val bridge = IosLlmBridgeRegistry.current()
    if (bridge == null) {
        println(
            "DEBUG OnDeviceLlmEngineProvider(iOS): Keine Swift-Llama-Bridge registriert. Nutze Fallback."
        )
        return null
    }

    if (modelPath.isBlank()) {
        println(
            "DEBUG OnDeviceLlmEngineProvider(iOS): LLAMA_MODEL_PATH ist leer, " +
                "Swift-Bridge versucht Bundle/Documents-Fallbacks."
        )
    }

    return BridgedOnDeviceLlmEngine(
        modelPath = modelPath,
        bridge = bridge,
    )
}
