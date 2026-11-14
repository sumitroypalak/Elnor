package com.elnor.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

// Everything under the text card of the note page (split out of EditorScreen.kt, nothing changed in
// how it works).

/**
 * The "not saved" line, the row with the formatting buttons and the link button, and the word and
 * character count. They are three children of the column of the page, in this order.
 *
 * [statsText] is the finished count text ("12 words · 68 characters"). [lastLink] is a one-place
 * memory that the page keeps: the link button shows the last link while it fades out, after the
 * cursor has already left it.
 */
@Composable
internal fun ColumnScope.EditorBottomArea(
    saveFailed: Boolean,
    bodyFocused: Boolean,
    compactHeight: Boolean,
    statsText: String,
    activeLink: LinkSpan?,
    lastLink: Array<LinkSpan?>,
    onBold: () -> Unit,
    onHeading: () -> Unit,
    onBullet: () -> Unit,
    onNumbered: () -> Unit
) {
    val context = LocalContext.current

    // Stays on the screen as long as the last save has not worked
    if (saveFailed) {
        Text(
            "Not saved yet. Elnor will try again when you type or leave.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        )
    }

    // Bold, heading and list buttons for the text (not for the title). The button for the
    // link the cursor is in (Call / Email / Open) shares the row, so it takes no extra height.
    // The row rises from the bottom and fades in when the text gets the focus
    AnimatedVisibility(
        visible = bodyFocused,
        enter = expandVertically(Motion.short()) +
            slideInVertically(Motion.short()) { it / 2 } + fadeIn(Motion.short()),
        exit = shrinkVertically(Motion.short()) +
            slideOutVertically(Motion.short()) { it / 2 } + fadeOut(Motion.short())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FormatToolbar(
                compact = compactHeight,
                onBold = onBold,
                onHeading = onHeading,
                onBullet = onBullet,
                onNumbered = onNumbered
            )
            // The link button fades in and out with a small zoom. While it goes out the
            // last link is still shown (the cursor has already left it).
            if (activeLink != null) lastLink[0] = activeLink
            AnimatedVisibility(
                visible = activeLink != null,
                modifier = Modifier.weight(1f, fill = false),
                enter = fadeIn(Motion.short()) + scaleIn(Motion.short(), initialScale = 0.85f),
                exit = fadeOut(Motion.short()) + scaleOut(Motion.short(), targetScale = 0.85f)
            ) {
                val shownLink = lastLink[0]
                if (shownLink != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Spacer(Modifier.width(8.dp))
                        LinkActionChip(
                            link = shownLink,
                            onClick = {
                                AppLock.expectExternalScreen() // the dialer / browser is not "leaving" Elnor
                                openLink(context, shownLink)
                            },
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }
                }
            }
            // A phone held sideways: the count shares the row of the buttons, so the
            // text keeps one more line
            if (compactHeight) {
                Spacer(Modifier.weight(1f))
                Text(
                    statsText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                    maxLines = 1,
                    modifier = Modifier.padding(start = 8.dp, end = 4.dp)
                )
            }
        }
    }

    // Word and character count, just above the keyboard (in the row of the buttons
    // instead, while they are shown on a phone held sideways)
    if (!(compactHeight && bodyFocused)) Text(
        statsText,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
    )
}
