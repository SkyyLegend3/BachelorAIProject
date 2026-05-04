package com.example.bachelor_ai_project.features.recording.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.bachelor_ai_project.app.designsystem.BaCard
import com.example.bachelor_ai_project.app.designsystem.BaColors
import com.example.bachelor_ai_project.app.designsystem.BaOutlinedButton
import com.example.bachelor_ai_project.app.designsystem.BaPrimaryButton
import com.example.bachelor_ai_project.features.recording.presentation.RecordingViewModel
import kotlinx.coroutines.delay

@Composable
fun RecordingScreen(
    viewModel: RecordingViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()

    RequestRecordAudioPermission(
        onGranted = viewModel::onPermissionGranted,
        onDenied = viewModel::onPermissionDenied,
    )

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

    var recordingSeconds by remember { mutableIntStateOf(0) }
    LaunchedEffect(state.isRecording) {
        if (state.isRecording) {
            recordingSeconds = 0
            while (true) {
                delay(1000)
                recordingSeconds += 1
            }
        } else {
            recordingSeconds = 0
        }
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        BaPrimaryButton(
            onClick = viewModel::toggleRecording,
            enabled = state.isPermissionGranted,
            danger = state.isRecording,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = if (state.isRecording) {
                    "● Aufnahme stoppen ${recordingSeconds.toClockString()}"
                } else {
                    "🎙 Aufnahme starten"
                },
                style = MaterialTheme.typography.labelLarge,
            )
        }

        BaOutlinedButton(
            onClick = viewModel::startFileSelection,
            enabled = !state.isRecording && state.uploadedFilePath == null && !state.isLoadingFile,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.isLoadingFile) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = BaColors.Primary,
                )
                Spacer(Modifier.size(8.dp))
            }
            Text("📁 Datei hochladen", style = MaterialTheme.typography.labelLarge)
        }

        state.uploadedFilePath?.let { uploadedFilePath ->
            FileUploadSection(
                uploadedFilePath = uploadedFilePath,
                onClearFile = viewModel::clearUploadedFile,
                onConfirmFile = viewModel::confirmUploadedFileSelection,
            )
        }

        state.error?.let { error ->
            Text(
                text = error,
                color = BaColors.Error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Start,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun FileUploadSection(
    uploadedFilePath: String,
    onClearFile: () -> Unit,
    onConfirmFile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val fileName = uploadedFilePath.substringAfterLast('/').replace("%20", " ")

    BaCard(modifier = modifier.fillMaxWidth()) {
        Text(
            text = fileName,
            style = MaterialTheme.typography.bodySmall,
            color = BaColors.TextSecondary,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BaOutlinedButton(
                onClick = onClearFile,
                modifier = Modifier.weight(1f),
            ) {
                Text("X", style = MaterialTheme.typography.labelLarge)
            }

            BaPrimaryButton(
                onClick = onConfirmFile,
                modifier = Modifier.weight(1f),
            ) {
                Text("✓", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

private fun Int.toClockString(): String {
    val minutes = this / 60
    val seconds = this % 60
    return "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
}
