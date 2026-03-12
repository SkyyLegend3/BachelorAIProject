package com.example.bachelor_ai_project.features.recording.ui

import androidx.compose.runtime.Composable

/**
 * Plattformübergreifende expect-Deklaration für die Mikrofon-Permission.
 * Actual-Implementierungen befinden sich in androidMain / iosMain.
 */
@Composable
expect fun RequestRecordAudioPermission(
    onGranted: () -> Unit,
    onDenied: () -> Unit = {},
)

