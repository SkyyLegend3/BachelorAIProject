package com.example.bachelor_ai_project.features.form.data

import com.example.bachelor_ai_project.core.config.AppConfig
import com.example.bachelor_ai_project.core.util.AndroidNativeRuntimeVerifier
import com.example.bachelor_ai_project.features.recording.domain.AppContextHolder
import java.io.File

/**
 * Zentrale Stelle fuer die Android-Lokal-LLM-Engine.
 *
 * Liefert eine llama.cpp-Engine, sobald ein Modellpfad konfiguriert ist.
 */
fun createDefaultOnDeviceLlmEngine(): OnDeviceLlmEngine? {
	val modelPath = AppConfig.llamaModelPath.trim()
	if (modelPath.isBlank()) {
		println("DEBUG OnDeviceLlmEngineProvider(Android): LLAMA_MODEL_PATH leer, LLM-Mapping deaktiviert")
		return null
	}
	if (!AndroidNativeRuntimeVerifier.ensureRealRuntime(
		componentTag = "OnDeviceLlmEngineProvider(Android)",
		runtimeClassName = "com.arm.aichat.AiChat",
	)) {
		return null
	}

	val modelFile = resolveUsableModelFile(modelPath) ?: return null

	return LlamaCppOnDeviceLlmEngine(modelPath = modelFile.absolutePath)
}

private fun resolveUsableModelFile(configuredPath: String): File? {
	val configuredFile = File(configuredPath)
	if (configuredFile.exists() && configuredFile.isFile && configuredFile.canRead()) {
		return configuredFile
	}

	val context = runCatching { AppContextHolder.applicationContext }.getOrNull()
	if (context == null) {
		println(
			"DEBUG OnDeviceLlmEngineProvider(Android): Context noch nicht initialisiert und Modell fehlt " +
				"($configuredPath), LLM-Mapping deaktiviert"
		)
		return null
	}

	val hasBundledAsset = runCatching {
		context.assets.open(ASSET_LLAMA_MODEL_PATH).use { true }
	}.getOrDefault(false)

	if (!hasBundledAsset) {
		println(
			"DEBUG OnDeviceLlmEngineProvider(Android): Llama-Modell nicht nutzbar ($configuredPath) und " +
				"kein Bundle-Asset unter $ASSET_LLAMA_MODEL_PATH gefunden, LLM-Mapping deaktiviert"
		)
		return null
	}

	return runCatching {
		configuredFile.parentFile?.mkdirs()
		context.assets.open(ASSET_LLAMA_MODEL_PATH).buffered().use { input ->
			configuredFile.outputStream().buffered().use { output ->
				input.copyTo(output)
				output.flush()
			}
		}
		println(
			"DEBUG OnDeviceLlmEngineProvider(Android): Llama-Modell aus Asset nach " +
				"${configuredFile.absolutePath} kopiert"
		)
		configuredFile
	}.getOrElse { error ->
		println(
			"DEBUG OnDeviceLlmEngineProvider(Android): Asset-Installation fehlgeschlagen: " +
				"${error.message}, LLM-Mapping deaktiviert"
		)
		null
	}
}

private const val ASSET_LLAMA_MODEL_PATH = "models/model.gguf"

