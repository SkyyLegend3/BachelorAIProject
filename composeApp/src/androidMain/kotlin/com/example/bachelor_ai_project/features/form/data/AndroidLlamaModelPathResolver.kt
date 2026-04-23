package com.example.bachelor_ai_project.features.form.data

import com.example.bachelor_ai_project.features.recording.domain.AppContextHolder
import java.io.File

/**
 * Loest robuste Android-Modellpfade fuer das lokale LLM auf.
 *
 * Hintergrund:
 * Der in BuildConfig hinterlegte Pfad kann nach Reinstall/Push variieren
 * (z. B. Dateiname gewechselt auf model.gguf). Dann faellt die UI sonst auf
 * "LLM-Model nicht gefunden" zurueck, obwohl in files/models bereits ein
 * gueltiges GGUF liegt.
 */
internal object AndroidLlamaModelPathResolver {

    fun resolveExistingModelPath(configuredPath: String): String? {
        val normalizedConfigured = configuredPath.trim()
        val candidates = linkedSetOf<String>()

        if (normalizedConfigured.isNotBlank()) {
            candidates += normalizedConfigured

            // Wenn der konfigurierte Name nicht mehr passt, den Standardnamen im selben Ordner pruefen.
            val configuredParent = runCatching { File(normalizedConfigured).parentFile }.getOrNull()
            configuredParent
                ?.resolve(DEFAULT_MODEL_FILE_NAME)
                ?.absolutePath
                ?.let(candidates::add)
        }

        resolveFilesDirModelCandidates().forEach(candidates::add)

        return candidates
            .asSequence()
            .map(::File)
            .firstOrNull { it.exists() && it.isFile && it.canRead() }
            ?.absolutePath
    }

    private fun resolveFilesDirModelCandidates(): List<String> {
        val appContext = runCatching { AppContextHolder.applicationContext }.getOrNull() ?: return emptyList()
        val modelDir = File(appContext.filesDir, "models")
        if (!modelDir.exists() || !modelDir.isDirectory) return emptyList()

        val ggufCandidates = modelDir
            .listFiles()
            .orEmpty()
            .filter { file -> file.isFile && file.canRead() && file.extension.equals("gguf", ignoreCase = true) }
            .sortedByDescending { it.length() }
            .map { it.absolutePath }

        val defaultPath = File(modelDir, DEFAULT_MODEL_FILE_NAME).absolutePath
        return listOf(defaultPath) + ggufCandidates
    }

    private const val DEFAULT_MODEL_FILE_NAME = "model.gguf"
}


