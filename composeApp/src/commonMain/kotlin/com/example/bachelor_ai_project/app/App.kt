package com.example.bachelor_ai_project.app

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeContentPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.bachelor_ai_project.app.designsystem.BaColors
import com.example.bachelor_ai_project.app.designsystem.BachelorAITheme
import com.example.bachelor_ai_project.features.form.ui.FormScreen
import com.example.bachelor_ai_project.features.recording.ui.RecordingScreen
import com.example.bachelor_ai_project.features.transcription.ui.TranscriptionScreen

@Composable
fun App() {
    BachelorAITheme {
        val appViewModel: AppViewModel = viewModel(factory = AppViewModelFactory())
        val formState by appViewModel.formViewModel.uiState.collectAsState()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(BaColors.Background)
                .safeContentPadding(),
        ) {
            AppHeader(
                isLlmModelConfigured = formState.isOnDeviceLlmModelConfigured,
                isLlmModelReady = formState.isOnDeviceLlmModelReady,
                isLlmModelLoading = formState.isOnDeviceLlmModelLoading,
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                RecordingScreen(
                    viewModel = appViewModel.recordingViewModel,
                    modifier = Modifier.fillMaxWidth(),
                )

                TranscriptionScreen(
                    viewModel = appViewModel.transcriptionViewModel,
                    modifier = Modifier.fillMaxWidth(),
                )

                FormScreen(
                    viewModel = appViewModel.formViewModel,
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun AppHeader(
    isLlmModelConfigured: Boolean,
    isLlmModelReady: Boolean,
    isLlmModelLoading: Boolean,
) {
    val llmStatusText = when {
        isLlmModelReady -> "LLM bereit"
        isLlmModelLoading -> "LLM-Model lädt"
        isLlmModelConfigured -> "LLM-Model gefunden"
        else -> "LLM-Model nicht gefunden"
    }
    val llmStatusColor = when {
        isLlmModelReady -> BaColors.Success
        isLlmModelLoading -> BaColors.Warning
        isLlmModelConfigured -> BaColors.PrimaryLight
        else -> BaColors.Error
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = BaColors.Primary,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = "BA Rhein-Main",
                    style = MaterialTheme.typography.labelLarge,
                    color = BaColors.White,
                )
                Text(
                    text = "Formularautomatisierung",
                    style = MaterialTheme.typography.labelSmall,
                    color = BaColors.White.copy(alpha = 0.8f),
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(llmStatusColor),
                )
                Text(
                    text = llmStatusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = BaColors.White.copy(alpha = 0.9f),
                )
            }
        }
    }
}
