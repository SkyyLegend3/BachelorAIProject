package com.example.bachelor_ai_project.core.util

/**
 * Plattform-Abstraktion für Audio-Datei-Picker.
 * Ermöglicht es, eine Audio-Datei vom Gerät auszuwählen.
 */
interface AudioFilePicker {
    /**
     * Öffnet einen Datei-Browser zur Auswahl einer Audio-Datei.
     * Ruft [onFileSelected] mit dem Dateipfad auf, wenn eine Datei ausgewählt wurde.
     * Ruft [onError] auf, wenn ein Fehler auftritt oder der Benutzer abbricht.
     */
    suspend fun pickAudioFile(
        onFileSelected: (filePath: String) -> Unit,
        onError: (errorMessage: String) -> Unit,
    )
}

expect fun createAudioFilePicker(): AudioFilePicker

