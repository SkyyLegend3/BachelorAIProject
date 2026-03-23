package com.example.bachelor_ai_project.features.recording.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.bachelor_ai_project.features.recording.presentation.RecordingViewModel

/**
 * Haupt-Composable des Recording-Features.
 *
 * Verantwortlichkeiten:
 * - Anfordern der Mikrofon-Permission
 * - Aufnahme starten / stoppen
 * - Datei-Upload ermöglichen
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

    // Datei-Picker öffnen, wenn shouldOpenFilePicker true ist
    RequestAudioFilePicker(
        shouldPickFile = state.shouldOpenFilePicker,
        onFileSelected = { filePath ->
            viewModel.onFileSelected(filePath)
            viewModel.onFilePickerClosed()
        },
        onError = { errorMessage ->
            viewModel.onFilePickerError(errorMessage)
            viewModel.onFilePickerClosed()
        },
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

        // Datei-Upload Section
        FileUploadSection(
            uploadedFilePath = state.uploadedFilePath,
            isLoading = state.isLoadingFile,
            enabled = state.isPermissionGranted && !state.isRecording,
            onStartSelection = viewModel::startFileSelection,
            onClearFile = viewModel::clearUploadedFile,
            onConfirmFile = viewModel::confirmUploadedFileSelection,
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

@Composable
private fun FileUploadSection(
    uploadedFilePath: String?,
    isLoading: Boolean,
    enabled: Boolean,
    onStartSelection: () -> Unit,
    onClearFile: () -> Unit,
    onConfirmFile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Spacer(Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = onStartSelection,
                enabled = enabled && uploadedFilePath == null && !isLoading,
                modifier = Modifier.weight(1f),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .width(18.dp)
                            .height(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text("📁 Datei hochladen")
            }

            if (uploadedFilePath != null) {
                val actionColors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                )

                Button(
                    onClick = onClearFile,
                    colors = actionColors,
                    modifier = Modifier.weight(0.2f),
                ) {
                    Text(
                        text = "X",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }

                Button(
                    onClick = onConfirmFile,
                    colors = actionColors,
                    modifier = Modifier.weight(0.2f),
                ) {
                    Text(
                        text = "✓",
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        if (uploadedFilePath != null) {
            val fileName = uploadedFilePath.substringAfterLast('/').replace("%20", " ")
            Text(
                text = fileName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

