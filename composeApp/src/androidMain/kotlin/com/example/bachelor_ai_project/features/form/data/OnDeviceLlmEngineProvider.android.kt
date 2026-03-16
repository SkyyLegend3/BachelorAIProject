package com.example.bachelor_ai_project.features.form.data

import com.example.bachelor_ai_project.core.config.AppConfig

/**
 * Zentrale Stelle fuer die Android-Lokal-LLM-Engine.
 *
 * Liefert eine llama.cpp-Engine, sobald ein Modellpfad konfiguriert ist.
 */
fun createDefaultOnDeviceLlmEngine(): OnDeviceLlmEngine? {
	val modelPath = AppConfig.llamaModelPath.trim()
	if (modelPath.isBlank()) return null

	return LlamaCppOnDeviceLlmEngine(modelPath = modelPath)
}

