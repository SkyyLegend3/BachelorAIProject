package com.example.bachelor_ai_project.core.util

import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * iOS-Implementierung des Audio-Datei-Pickers.
 * Hinweis: Der produktive iOS-Dateipicker läuft über
 * `RequestAudioFilePickerImpl.ios.kt` direkt per UIDocumentPicker.
 * Diese Klasse bleibt nur als Fallback/Kompatibilität erhalten.
 */
class IOSAudioFilePicker : AudioFilePicker {

    override suspend fun pickAudioFile(
        onFileSelected: (filePath: String) -> Unit,
        onError: (errorMessage: String) -> Unit,
    ) {
        suspendCancellableCoroutine { continuation ->
            onError("iOS Fallback-AudioFilePicker ist nicht aktiv. Nutze den UI-Dateipicker im Recording-Screen.")
            continuation.resume(Unit)
        }
    }
}

actual fun createAudioFilePicker(): AudioFilePicker = IOSAudioFilePicker()

