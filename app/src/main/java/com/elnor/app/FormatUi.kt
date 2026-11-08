package com.elnor.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The formatting buttons above the keyboard while the text of a note is being edited: Bold,
 * Heading (press again for a smaller heading, again to remove it), Bullet list and Numbered
 * list. Each button changes the text in the editor, see [MarkdownFormat].
 *
 * It is a floating pill, drawn like the bottom bar of the home screen (same colours, shadow and
 * edge, see [bottomBarColors]), so it follows the light, dark and wallpaper colours.
 */
@Composable
fun FormatToolbar(
    onBold: () -> Unit,
    onHeading: () -> Unit,
    onBullet: () -> Unit,
    onNumbered: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val dark = scheme.surface.luminance() < 0.5f
    val colors = bottomBarColors(scheme, dark)

    Surface(
        modifier = modifier.padding(vertical = 6.dp),
        shape = CircleShape,
        color = colors.bar,
        shadowElevation = 6.dp,
        // A white pill on the light page needs a thin edge besides the shadow
        border = if (dark) null else BorderStroke(1.dp, scheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            FormatTextButton("B", "Bold", FontWeight.ExtraBold, colors.onBar, onBold)
            FormatTextButton("H", "Heading", FontWeight.Bold, colors.onBar, onHeading)
            FormatIconButton(BulletListIcon, "Bullet list", colors.onBar, onBullet)
            FormatIconButton(NumberedListIcon, "Numbered list", colors.onBar, onNumbered)
        }
    }
}

@Composable
private fun FormatTextButton(
    label: String,
    description: String,
    weight: FontWeight,
    tint: Color,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.semantics { contentDescription = description }
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = weight,
            color = tint
        )
    }
}

@Composable
private fun FormatIconButton(
    icon: ImageVector,
    description: String,
    tint: Color,
    onClick: () -> Unit
) {
    IconButton(onClick = onClick) {
        Icon(icon, contentDescription = description, tint = tint)
    }
}
