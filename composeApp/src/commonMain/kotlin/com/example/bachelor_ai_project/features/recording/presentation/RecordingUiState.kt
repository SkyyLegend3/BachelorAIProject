package com.example.bachelor_ai_project.features.recording.presentation

/**
 * Repräsentiert den gesamten UI-Zustand des Recording-Screens.
 * Unveränderliche Data-Class – der ViewModel erzeugt immer eine neue Kopie.
 */
data class RecordingUiState(
    val isRecording: Boolean = false,
    val isPermissionGranted: Boolean = false,
    val recordingFilePath: String? = null,
    val error: String? = null,
)

