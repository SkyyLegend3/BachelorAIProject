package com.example.bachelor_ai_project.features.recording.ui

import androidx.compose.runtime.Composable

/**
 * Composable, das einen Audio-Datei-Picker öffnet.
 * Die Implementierung ist plattformspezifisch (Android/iOS).
 */
@Composable
fun RequestAudioFilePicker(
    shouldPickFile: Boolean,
    onFileSelected: (filePath: String) -> Unit,
    onError: (errorMessage: String) -> Unit,
) {
    RequestAudioFilePickerImpl(
        shouldPickFile = shouldPickFile,
        onFileSelected = onFileSelected,
        onError = onError,
    )
}

/**
 * Plattformspezifische Implementierung des Audio-Datei-Pickers.
 * Wird als expect/actual definiert für Android und iOS.
 */
@Composable
expect fun RequestAudioFilePickerImpl(
    shouldPickFile: Boolean,
    onFileSelected: (filePath: String) -> Unit,
    onError: (errorMessage: String) -> Unit,
)


