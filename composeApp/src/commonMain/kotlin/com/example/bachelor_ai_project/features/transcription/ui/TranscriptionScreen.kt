package com.example.bachelor_ai_project.features.transcription.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.bachelor_ai_project.features.transcription.domain.TranscriptSegment
import com.example.bachelor_ai_project.features.transcription.presentation.TranscriptionViewModel

/**
 * Composable, das das Transkriptionsergebnis darstellt.
 * Zeigt einen Ladeindikator, eine Fehlermeldung oder die gruppierten Sprecher-Segmente.
 */
@Composable
fun TranscriptionScreen(
    viewModel: TranscriptionViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        when {
            state.isLoading -> TranscriptionLoadingIndicator()
            state.error != null -> TranscriptionError(message = state.error!!)
            state.hasResult -> TranscriptionResult(segments = state.segments)
        }
    }
}

@Composable
private fun TranscriptionLoadingIndicator() {
    CircularProgressIndicator()
    Spacer(Modifier.height(4.dp))
    Text(
        text = "Transkription läuft…",
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun TranscriptionError(message: String) {
    Text(
        text = message,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun TranscriptionResult(segments: List<TranscriptSegment>) {
    Text("Transkript", style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(4.dp))

    val speakers = segments.map { it.speaker }.distinct().sorted()
    speakers.forEach { speaker ->
        SpeakerCard(
            speaker = speaker,
            segments = segments.filter { it.speaker == speaker },
        )
    }
}

@Composable
private fun SpeakerCard(
    speaker: String,
    segments: List<TranscriptSegment>,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = "🗣 ${speaker.replace("_", " ").replaceFirstChar { it.uppercase() }}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
            segments.forEach { segment ->
                Text(
                    text = segment.text.trim(),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

