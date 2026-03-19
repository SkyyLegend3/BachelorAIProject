package com.example.bachelor_ai_project.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.bachelor_ai_project.features.form.domain.FormAutomationMode
import com.example.bachelor_ai_project.features.form.presentation.FormViewModel
import com.example.bachelor_ai_project.features.transcription.domain.WhisperLocalModel
import com.example.bachelor_ai_project.features.transcription.presentation.TranscriptionViewModel

/**
 * Gemeinsame Steuerung fuer Automatisierungsmodus und (optional) Android-Whisper-Modell.
 */
@Composable
fun AutomationSettingsCard(
    formViewModel: FormViewModel,
    transcriptionViewModel: TranscriptionViewModel,
    modifier: Modifier = Modifier,
) {
    val formState by formViewModel.uiState.collectAsState()
    val transcriptionState by transcriptionViewModel.uiState.collectAsState()

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Automatisierung",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ModeButton(
                    text = "Cloud",
                    selected = formState.automationMode == FormAutomationMode.CLOUD,
                    modifier = Modifier.weight(1f),
                    onClick = { formViewModel.setAutomationMode(FormAutomationMode.CLOUD) },
                )

                ModeButton(
                    text = "On Device",
                    selected = formState.automationMode == FormAutomationMode.ON_DEVICE,
                    enabled = formState.supportsOnDeviceMapping,
                    modifier = Modifier.weight(1f),
                    onClick = { formViewModel.setAutomationMode(FormAutomationMode.ON_DEVICE) },
                )
            }

            if (!formState.supportsOnDeviceMapping) {
                Text(
                    text = "On-Device-Mapping ist aktuell nur auf Android verfuegbar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            val shouldShowWhisperOptions =
                formState.automationMode == FormAutomationMode.ON_DEVICE &&
                    transcriptionState.onDeviceModelState.supportsModelManagement

            AnimatedVisibility(
                visible = shouldShowWhisperOptions,
                enter = fadeIn(animationSpec = tween(durationMillis = 160)) +
                    expandVertically(animationSpec = tween(durationMillis = 200)),
                exit = fadeOut(animationSpec = tween(durationMillis = 120)) +
                    shrinkVertically(animationSpec = tween(durationMillis = 150)),
            ) {
                val whisperState = transcriptionState.onDeviceModelState
                val activeModelLabel = if (whisperState.selectedModel == WhisperLocalModel.SMALL) "Small" else "Base"
                val installActionLabel = when {
                    whisperState.canInstallSmallModelFromBundle -> "Whisper Small aus App-Bundle installieren"
                    whisperState.canDownloadSmallModel -> "Whisper Small herunterladen"
                    else -> "Whisper Small nicht verfuegbar"
                }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Whisper-Modell",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "On-Device-Transkription (Android)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Badge {
                            Text("Aktiv: $activeModelLabel")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ModeButton(
                            text = "Base",
                            selected = whisperState.selectedModel == WhisperLocalModel.BASE,
                            modifier = Modifier.weight(1f),
                            onClick = { transcriptionViewModel.selectOnDeviceModel(WhisperLocalModel.BASE) },
                        )
                        ModeButton(
                            text = "Small",
                            selected = whisperState.selectedModel == WhisperLocalModel.SMALL,
                            enabled = whisperState.isSmallModelDownloaded,
                            modifier = Modifier.weight(1f),
                            onClick = { transcriptionViewModel.selectOnDeviceModel(WhisperLocalModel.SMALL) },
                        )
                    }

                    if (!whisperState.isSmallModelDownloaded) {
                        Button(
                            onClick = transcriptionViewModel::prepareOnDeviceSmallModel,
                            enabled = (whisperState.canInstallSmallModelFromBundle || whisperState.canDownloadSmallModel) &&
                                !whisperState.isDownloadingSmallModel,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(installActionLabel)
                        }
                    }

                    if (whisperState.isDownloadingSmallModel) {
                        val progressText = whisperState.smallModelDownloadProgressPercent?.let { "$it%" } ?: "..."
                        Text(
                            text = "Bereitstellung laeuft ($progressText)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    whisperState.smallModelStatusMessage?.let { message ->
                        Text(
                            text = message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }

                    whisperState.lastError?.let { error ->
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeButton(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
        ) {
            Text(text)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
        ) {
            Text(text)
        }
    }
}

