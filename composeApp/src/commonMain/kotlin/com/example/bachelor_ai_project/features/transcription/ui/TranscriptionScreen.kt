package com.example.bachelor_ai_project.features.transcription.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import com.example.bachelor_ai_project.app.designsystem.BaSectionLabel
import com.example.bachelor_ai_project.features.transcription.domain.TranscriptSegment
import com.example.bachelor_ai_project.features.transcription.presentation.TranscriptionViewModel

@Composable
fun TranscriptionScreen(
    viewModel: TranscriptionViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        when {
            state.isLoading -> TranscriptionLoadingIndicator()
            state.error != null -> TranscriptionError(message = state.error!!)
            state.hasResult -> TranscriptionResult(segments = state.segments)
        }

        if (state.debugLogs.isNotEmpty()) {
            BaCard(modifier = Modifier.fillMaxWidth()) {
                BaSectionLabel(text = "Transkriptions-Log")
                Spacer(Modifier.height(6.dp))
                state.debugLogs.takeLast(6).forEach { line ->
                    Text(
                        text = "• $line",
                        style = MaterialTheme.typography.bodySmall,
                        color = BaColors.TextSecondary,
                    )
                }
            }
        }
    }
}

@Composable
private fun TranscriptionLoadingIndicator() {
    BaCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = BaColors.Primary,
            )
            Text(
                text = "Transkription laeuft...",
                style = MaterialTheme.typography.bodySmall,
                color = BaColors.TextSecondary,
            )
        }
    }
}

@Composable
private fun TranscriptionError(message: String) {
    Text(
        text = message,
        color = BaColors.Error,
        style = MaterialTheme.typography.bodySmall,
    )
}

@Composable
private fun TranscriptionResult(segments: List<TranscriptSegment>) {
    BaCard(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Transkript",
            style = MaterialTheme.typography.titleMedium,
            color = BaColors.Primary,
        )

        Spacer(Modifier.height(8.dp))

        val speakerRuns = segments.toSpeakerRuns()
        speakerRuns.forEach { run ->
            SpeakerCard(
                speaker = run.speaker,
                segments = run.segments,
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SpeakerCard(
    speaker: String,
    segments: List<TranscriptSegment>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(BaColors.Neutral50)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "🗣 ${speaker.replace("_", " ").replaceFirstChar { it.uppercase() }}",
            style = MaterialTheme.typography.labelLarge,
            color = BaColors.Primary,
        )
        segments.forEach { segment ->
            Text(
                text = segment.text.trim(),
                style = MaterialTheme.typography.bodyMedium,
                color = BaColors.TextPrimary,
            )
        }
    }
}

private data class SpeakerRun(
    val speaker: String,
    val segments: List<TranscriptSegment>,
)

private fun List<TranscriptSegment>.toSpeakerRuns(): List<SpeakerRun> {
    if (isEmpty()) return emptyList()

    val runs = mutableListOf<SpeakerRun>()
    for (segment in this) {
        val lastRun = runs.lastOrNull()
        if (lastRun != null && lastRun.speaker == segment.speaker) {
            runs[runs.lastIndex] = lastRun.copy(
                segments = lastRun.segments + segment,
            )
        } else {
            runs += SpeakerRun(
                speaker = segment.speaker,
                segments = listOf(segment),
            )
        }
    }
    return runs
}
