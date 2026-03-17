package com.example.bachelor_ai_project.features.form.data

/**
 * Minimales Interface fuer lokale LLM-Inferenz auf iOS.
 *
 * Eine konkrete Runtime (z. B. llama.cpp) kann spaeter ueber dieses Interface angebunden werden.
 */
interface OnDeviceLlmEngine {
    suspend fun completeJson(systemPrompt: String, userPrompt: String): String
}

