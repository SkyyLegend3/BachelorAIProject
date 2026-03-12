package com.example.bachelor_ai_project.core.util

/**
 * Plattform-Abstraktion für Pfade zu plattformspezifischen Verzeichnissen.
 * Actual-Implementierungen liegen in androidMain / iosMain.
 */
interface PlatformFileProvider {
    /** Gibt einen beschreibbaren Pfad für eine neue Aufnahme zurück. */
    fun recordingFilePath(): String
}

expect fun createPlatformFileProvider(): PlatformFileProvider

