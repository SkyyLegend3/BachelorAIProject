package com.example.bachelor_ai_project.features.form.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.bachelor_ai_project.features.form.domain.SpeakerBlock
import kotlin.math.roundToInt

/**
 * Zeigt alle [SpeakerBlock]s des gemappten Transkripts als lesbare Liste an.
 *
 * Jeder Block zeigt:
 * - Sprecher-Label (z.B. "SPEAKER_00")
 * - Zeitstempel (Start → Ende)
 * - Vollständigen Redetext
 */
@Composable
fun TranscriptPreview(
    blocks: List<SpeakerBlock>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Transkript",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        blocks.forEach { block ->
            SpeakerBlockItem(block = block)
        }
    }
}

@Composable
private fun SpeakerBlockItem(
    block: SpeakerBlock,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = block.speaker.ifBlank { "Unbekannt" },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = "${block.startSeconds.toTimestamp()} → ${block.endSeconds.toTimestamp()}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = block.text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Formatiert Sekunden als `mm:ss`. */
private fun Double.toTimestamp(): String {
    val total = this.roundToInt()
    val minutes = total / 60
    val seconds = total % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}


