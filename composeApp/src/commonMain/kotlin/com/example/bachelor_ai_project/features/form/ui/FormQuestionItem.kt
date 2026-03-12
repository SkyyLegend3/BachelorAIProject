package com.example.bachelor_ai_project.features.form.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.bachelor_ai_project.features.form.domain.FormEntry

/**
 * Darstellung einer einzelnen Formular-Frage mit Eingabefeld.
 *
 * @param entry     Kombiniertes Objekt aus Frage und aktuellem Antwortwert.
 * @param onValueChange Callback, wenn der Nutzer den Text ändert.
 */
@Composable
fun FormQuestionItem(
    entry: FormEntry,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = buildString {
                append(entry.question.label)
                if (entry.question.required) append(" *")
            },
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = entry.answer.value,
            onValueChange = onValueChange,
            placeholder = {
                if (entry.question.hint.isNotEmpty()) {
                    Text(
                        text = entry.question.hint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            isError = entry.question.required && entry.answer.value.isBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            singleLine = false,
            minLines = 2,
        )
    }
}

