package com.elnor.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The row of formatting buttons above the keyboard while the text of a note is being edited:
 * Bold, Heading (press again for a smaller heading, again to remove it), Bullet list and
 * Numbered list. Each button changes the text in the editor, see [MarkdownFormat].
 */
@Composable
fun FormatToolbar(
    onBold: () -> Unit,
    onHeading: () -> Unit,
    onBullet: () -> Unit,
    onNumbered: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        FormatButton("B", "Bold", FontWeight.ExtraBold, onBold)
        FormatButton("H", "Heading", FontWeight.Bold, onHeading)
        FormatButton("•", "Bullet list", FontWeight.Bold, onBullet)
        FormatButton("1.", "Numbered list", FontWeight.Bold, onNumbered)
    }
}

@Composable
private fun FormatButton(label: String, description: String, weight: FontWeight, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = description }
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = weight,
            color = MaterialTheme.colorScheme.primary
        )
    }
}
