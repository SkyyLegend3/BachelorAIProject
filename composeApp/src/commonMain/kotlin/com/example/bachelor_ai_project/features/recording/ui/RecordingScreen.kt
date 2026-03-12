package com.example.bachelor_ai_project.features.recording.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.bachelor_ai_project.features.recording.presentation.RecordingViewModel

/**
 * Haupt-Composable des Recording-Features.
 *
 * Verantwortlichkeiten:
 * - Anfordern der Mikrofon-Permission
 * - Aufnahme starten / stoppen
 * - Fehler anzeigen
 */
@Composable
fun RecordingScreen(
    viewModel: RecordingViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()

    // Permission einmalig beim Einblenden des Screens anfordern
    RequestRecordAudioPermission(
        onGranted = viewModel::onPermissionGranted,
        onDenied = viewModel::onPermissionDenied,
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RecordButton(
            isRecording = state.isRecording,
            enabled = state.isPermissionGranted,
            onClick = viewModel::toggleRecording,
        )

        state.error?.let { error ->
            Spacer(Modifier.height(4.dp))
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun RecordButton(
    isRecording: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        colors = if (isRecording) {
            ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
        } else {
            ButtonDefaults.buttonColors()
        },
        modifier = modifier,
    ) {
        Text(if (isRecording) "⏹ Aufnahme stoppen" else "🎙 Aufnahme starten")
    }
}

