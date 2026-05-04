package com.example.bachelor_ai_project.app.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun BaCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        color = BaColors.CardBackground,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, BaColors.BorderDefault),
        shadowElevation = 2.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            content = content,
        )
    }
}

@Composable
fun BaPrimaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    danger: Boolean = false,
    content: @Composable RowScope.() -> Unit,
) {
    val container = if (danger) BaColors.Error else BaColors.Primary

    Button(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(999.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = BaColors.White,
            disabledContainerColor = BaColors.BorderDefault,
            disabledContentColor = BaColors.TextDisabled,
        ),
        contentPadding = ButtonDefaults.ContentPadding,
        content = content,
    )
}

@Composable
fun BaOutlinedButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, BaColors.BorderDefault),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = BaColors.White,
            contentColor = BaColors.Primary,
            disabledContentColor = BaColors.TextDisabled,
        ),
        content = content,
    )
}

@Composable
fun BaSectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = BaColors.TextSecondary,
        letterSpacing = MaterialTheme.typography.labelSmall.letterSpacing,
        fontWeight = FontWeight.Bold,
    )
}

@Composable
fun BaStatusLine(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .background(color, CircleShape)
                .size(8.dp),
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = BaColors.TextSecondary,
        )
    }
}

@Composable
fun BaDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(
        modifier = modifier,
        color = BaColors.BorderDefault,
        thickness = 1.dp,
    )
}
