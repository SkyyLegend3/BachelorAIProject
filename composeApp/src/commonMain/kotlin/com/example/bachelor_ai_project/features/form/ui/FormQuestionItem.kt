package com.example.bachelor_ai_project.features.form.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.bachelor_ai_project.app.designsystem.BaColors
import com.example.bachelor_ai_project.features.form.domain.FormEntry

@Composable
fun FormQuestionItem(
    entry: FormEntry,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isMissingRequiredAnswer = entry.question.required && entry.answer.value.isBlank()
    val inputBackground = BaColors.Primary.copy(alpha = 0.06f)

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = buildString {
                append(entry.question.label)
                if (entry.question.required) append(" *")
            },
            style = MaterialTheme.typography.labelLarge,
            color = BaColors.TextPrimary,
        )

        Spacer(Modifier.height(6.dp))

        OutlinedTextField(
            value = entry.answer.value,
            onValueChange = onValueChange,
            placeholder = {
                if (entry.question.hint.isNotEmpty()) {
                    Text(
                        text = entry.question.hint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = BaColors.TextSecondary,
                    )
                }
            },
            isError = isMissingRequiredAnswer,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            singleLine = false,
            minLines = 2,
            shape = MaterialTheme.shapes.small,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = inputBackground,
                unfocusedContainerColor = inputBackground,
                disabledContainerColor = inputBackground,
                errorContainerColor = inputBackground,
                focusedBorderColor = BaColors.Primary,
                unfocusedBorderColor = BaColors.BorderDefault,
                errorBorderColor = BaColors.Error,
                focusedTextColor = BaColors.TextPrimary,
                unfocusedTextColor = BaColors.TextPrimary,
                errorTextColor = BaColors.TextPrimary,
                cursorColor = BaColors.Primary,
            ),
        )
    }
}
