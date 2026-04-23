package com.example.bachelor_ai_project.features.form.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.bachelor_ai_project.features.form.domain.FormAutomationMode
import com.example.bachelor_ai_project.features.form.presentation.FormViewModel

/**
 * Haupt-Composable für das Feedback-Formular.
 *
 * Zeigt alle konfigurierten Fragen als editierbare Felder an.
 * Neue Fragen werden automatisch gerendert, sobald sie im Form-Definition-Provider
 * eingetragen sind.
 * Oberhalb der Felder wird das Transkript als Sprecher-Blöcke angezeigt,
 * sofern eines vorliegt.
 */
@Composable
fun FormScreen(
    viewModel: FormViewModel,
    modifier: Modifier = Modifier,
    onSubmit: (() -> Unit)? = null,
) {
    val state by viewModel.uiState.collectAsState()

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shape = MaterialTheme.shapes.medium,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Feedback-Formular",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ModeButton(
                    text = "Cloud",
                    selected = state.automationMode == FormAutomationMode.CLOUD,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.setAutomationMode(FormAutomationMode.CLOUD) },
                )

                ModeButton(
                    text = "On Device",
                    selected = state.automationMode == FormAutomationMode.ON_DEVICE,
                    enabled = state.supportsOnDeviceMapping,
                    modifier = Modifier.weight(1f),
                    onClick = { viewModel.setAutomationMode(FormAutomationMode.ON_DEVICE) },
                )
            }

            if (state.automationMode == FormAutomationMode.ON_DEVICE && state.supportsOnDeviceMapping) {
                val indicatorColor = when {
                    state.isOnDeviceLlmModelLoading -> Color(0xFFF9A825)
                    state.isOnDeviceLlmModelReady -> Color(0xFF2E7D32)
                    state.isOnDeviceLlmModelConfigured -> Color(0xFFEF6C00)
                    else -> Color(0xFFC62828)
                }
                val indicatorText = when {
                    state.isOnDeviceLlmModelLoading -> "Model wird geladen"
                    state.isOnDeviceLlmModelReady -> "LLM-Model bereit"
                    state.isOnDeviceLlmModelConfigured -> "LLM-Model gefunden (noch nicht geladen)"
                    else -> "LLM-Model nicht gefunden"
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(indicatorColor)
                    )
                    Text(
                        text = indicatorText,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Black,
                    )
                }
            }

            if (!state.supportsOnDeviceMapping) {
                Text(
                    text = "On-Device-Mapping ist aktuell nur auf Android verfuegbar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (state.supportsOnDeviceMapping) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "On-Device Rechtschreibkorrektur",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = "Korrigiert lautnahe Whisper-Fehler vor dem Befuellen.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = state.onDeviceOrthographyCorrectionEnabled,
                        onCheckedChange = viewModel::setOnDeviceOrthographyCorrectionEnabled,
                        enabled = state.automationMode == FormAutomationMode.ON_DEVICE,
                    )
                }
            }

            // ── Transkript-Vorschau ────────────────────────────────────────────
            if (state.isMappingLoading) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(
                        text = "Transkript wird verarbeitet…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (state.mappingLogs.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "Prozess-Log",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    state.mappingLogs.takeLast(6).forEach { line ->
                        Text(
                            text = "• $line",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            state.mappingSourceError?.let { sourceError ->
                Text(
                    text = sourceError,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            state.mappingError?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (state.hasTranscript) {
                TranscriptPreview(blocks = state.speakerBlocks)
                HorizontalDivider()
            }

            // ── Formularfelder ─────────────────────────────────────────────────
            state.entries.forEach { entry ->
                FormQuestionItem(
                    entry = entry,
                    onValueChange = { newValue ->
                        viewModel.updateAnswer(entry.question.id, newValue)
                    },
                )
            }

            if (state.supportsOnDeviceMapping && state.automationMode == FormAutomationMode.ON_DEVICE) {
                Button(
                    onClick = viewModel::runOnDeviceLlmTest,
                    enabled = !state.isLlmTestRunning,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state.isLlmTestRunning) "LLM Test laeuft..." else "LLM Test")
                }

                state.llmTestSuccess?.let { success ->
                    Text(
                        text = if (success) "LLM funktioniert" else "LLM fail",
                        color = if (success) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            state.submitError?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(Modifier.height(4.dp))

            Button(
                onClick = { onSubmit?.invoke() },
                enabled = state.isValid && !state.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (state.isSubmitting) "Wird gesendet…" else "Absenden")
            }

            if (state.isSubmitted) {
                Text(
                    text = "✅ Formular erfolgreich abgesendet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
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

