package com.example.bachelor_ai_project.core.config

/**
 * Plattformübergreifende Konfiguration.
 *
 * Der API-Key wird zur Buildzeit über `local.properties` eingelesen
 * und nie in den Quellcode oder das VCS eingecheckt.
 *
 * - Android: BuildConfig.OPENAI_API_KEY (via buildConfigField in build.gradle.kts)
 * - iOS:     Info.plist-Eintraege OPENAI_API_KEY / LLAMA_MODEL_PATH / WHISPER_MODEL_PATH
 *            (aus xcconfig / Secrets)
 */
expect object AppConfig {
    val openAiApiKey: String
    val llamaModelPath: String
    val whisperModelPath: String
    val whisperSmallModelDownloadUrl: String
}

