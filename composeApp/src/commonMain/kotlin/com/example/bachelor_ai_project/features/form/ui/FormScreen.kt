package com.example.bachelor_ai_project.features.form.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.bachelor_ai_project.features.form.presentation.FormViewModel

/**
 * Haupt-Composable für das Feedback-Formular.
 *
 * Zeigt alle konfigurierten Fragen als editierbare Felder an.
 * Neue Fragen werden automatisch gerendert, sobald sie im
 * [DefaultFormDefinitionProvider] eingetragen sind.
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
