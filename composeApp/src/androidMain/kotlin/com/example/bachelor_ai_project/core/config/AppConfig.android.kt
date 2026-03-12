package com.example.bachelor_ai_project.core.config

import com.example.bachelor_ai_project.BuildConfig

actual object AppConfig {
    actual val openAiApiKey: String get() = BuildConfig.OPENAI_API_KEY
}

