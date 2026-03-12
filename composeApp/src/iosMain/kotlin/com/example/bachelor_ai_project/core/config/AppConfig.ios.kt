package com.example.bachelor_ai_project.core.config

import platform.Foundation.NSBundle

actual object AppConfig {
    actual val openAiApiKey: String
        get() = NSBundle.mainBundle.objectForInfoDictionaryKey("OPENAI_API_KEY") as? String ?: ""
}

