package com.example.bachelor_ai_project.features.transcription.domain

/**
 * Liest eine Datei als ByteArray.
 * Plattformspezifisch implementiert in androidMain / iosMain.
 */
expect fun readFileBytes(path: String): ByteArray

