package com.example.bachelor_ai_project.features.form.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.bachelor_ai_project.app.designsystem.BaCard
import com.example.bachelor_ai_project.app.designsystem.BaColors
import com.example.bachelor_ai_project.app.designsystem.BaDivider
import com.example.bachelor_ai_project.app.designsystem.BaPrimaryButton
import com.example.bachelor_ai_project.app.designsystem.BaSectionLabel
import com.example.bachelor_ai_project.features.form.domain.FormAutomationMode
import com.example.bachelor_ai_project.features.form.presentation.FormViewModel

@Composable
fun FormScreen(
    viewModel: FormViewModel,
    modifier: Modifier = Modifier,
    onSubmit: (() -> Unit)? = null,
) {
    val state by viewModel.uiState.collectAsState()

    BaCard(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Feedback-Formular",
                style = MaterialTheme.typography.titleMedium,
                color = BaColors.Primary,
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

            if (!state.supportsOnDeviceMapping) {
                Text(
                    text = "On-Device-Mapping ist aktuell nur auf Android verfuegbar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = BaColors.TextSecondary,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (state.supportsOnDeviceMapping && state.automationMode == FormAutomationMode.ON_DEVICE) {
                BaDivider(modifier = Modifier.fillMaxWidth())

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "On-Device Rechtschreibkorrektur",
                            style = MaterialTheme.typography.bodyMedium,
                            color = BaColors.TextPrimary,
                        )
                        Text(
                            text = "Korrigiert lautnahe Whisper-Fehler vor dem Befuellen.",
                            style = MaterialTheme.typography.bodySmall,
                            color = BaColors.TextSecondary,
                        )
                    }

                    Switch(
                        checked = state.onDeviceOrthographyCorrectionEnabled,
                        onCheckedChange = viewModel::setOnDeviceOrthographyCorrectionEnabled,
                        enabled = true,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = BaColors.White,
                            checkedTrackColor = BaColors.Primary,
                            uncheckedThumbColor = BaColors.White,
                            uncheckedTrackColor = BaColors.BorderDefault,
                        ),
                    )
                }
            }

            if (state.isMappingLoading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = BaColors.Primary,
                    )
                    Text(
                        text = "Transkript wird verarbeitet...",
                        style = MaterialTheme.typography.bodySmall,
                        color = BaColors.TextSecondary,
                    )
                }
            }

            if (state.mappingLogs.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    BaSectionLabel(text = "Prozess-Log")
                    state.mappingLogs.takeLast(6).forEach { line ->
                        Text(
                            text = "• $line",
                            style = MaterialTheme.typography.bodySmall,
                            color = BaColors.TextSecondary,
                        )
                    }
                }
            }

            state.mappingSourceError?.let { sourceError ->
                Text(
                    text = sourceError,
                    color = BaColors.Error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            state.mappingError?.let { error ->
                Text(
                    text = error,
                    color = BaColors.Error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (state.hasTranscript) {
                TranscriptPreview(blocks = state.speakerBlocks)
                BaDivider(modifier = Modifier.padding(top = 2.dp))
            }

            state.entries.forEach { entry ->
                FormQuestionItem(
                    entry = entry,
                    onValueChange = { newValue ->
                        viewModel.updateAnswer(entry.question.id, newValue)
                    },
                )
            }

            if (state.supportsOnDeviceMapping && state.automationMode == FormAutomationMode.ON_DEVICE) {
                BaPrimaryButton(
                    onClick = viewModel::runOnDeviceLlmTest,
                    enabled = !state.isLlmTestRunning,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        if (state.isLlmTestRunning) "LLM Test laeuft..." else "LLM Test",
                        style = MaterialTheme.typography.labelLarge,
                    )
                }

                state.llmTestSuccess?.let { success ->
                    Text(
                        text = if (success) "LLM funktioniert" else "LLM fail",
                        color = if (success) BaColors.Success else BaColors.Error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            state.submitError?.let { error ->
                Text(
                    text = error,
                    color = BaColors.Error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(2.dp))

            BaPrimaryButton(
                onClick = {
                    if (onSubmit != null) {
                        onSubmit()
                    } else {
                        viewModel.submitForm()
                    }
                },
                enabled = state.isValid && !state.isSubmitting,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (state.isSubmitting) "Wird gesendet..." else "Absenden",
                    style = MaterialTheme.typography.labelLarge,
                )
            }

            if (state.isSubmitted) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(BaColors.SuccessBg)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(BaColors.Success),
                    )
                    Text(
                        text = "Formular erfolgreich abgesendet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = BaColors.Success,
                    )
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
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = BaColors.Primary,
                contentColor = BaColors.White,
                disabledContainerColor = BaColors.BorderDefault,
                disabledContentColor = BaColors.TextDisabled,
            ),
        ) {
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = BaColors.White,
                contentColor = BaColors.Primary,
                disabledContentColor = BaColors.TextDisabled,
            ),
        ) {
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}
