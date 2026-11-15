package com.elnor.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp

// The reading view of a note: the formatting marks are hidden and the text is drawn as headings,
// bold words, bullet and numbered lists. What is drawn comes from [MarkdownPreview], which has the
// rules and is tested on the computer. This file only draws it.

/**
 * The note text as a nice reading page. It fills the space it is given and scrolls. It never
 * changes the note. A tap on a web link, an e-mail address or a phone number calls [onOpenLink].
 */
@Composable
internal fun MarkdownPreviewView(
    text: String,
    onOpenLink: (LinkSpan) -> Unit,
    modifier: Modifier = Modifier
) {
    // Calculated again only when the text changes (not on every redraw)
    val blocks = remember(text) { MarkdownPreview.parse(text) }

    if (blocks.isEmpty()) {
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Text(
                "Nothing to preview yet",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
            )
        }
        return
    }

    val accent = MaterialTheme.colorScheme.primary
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        itemsIndexed(blocks) { index, block ->
            PreviewBlockView(
                block = block,
                first = index == 0,
                accent = accent,
                onOpenLink = onOpenLink
            )
        }
    }
}

@Composable
private fun PreviewBlockView(
    block: PreviewBlock,
    first: Boolean,
    accent: Color,
    onOpenLink: (LinkSpan) -> Unit
) {
    val typography = MaterialTheme.typography
    val body = typography.bodyLarge
    // Rebuilt only when the block or the colour changes
    val shown = remember(block, accent) { previewText(block, accent, onOpenLink) }

    when (block.kind) {
        PreviewKind.BLANK -> Spacer(Modifier.height(8.dp))

        PreviewKind.HEADING1 -> Text(
            shown,
            style = typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(top = if (first) 0.dp else 8.dp)
        )

        PreviewKind.HEADING2 -> Text(
            shown,
            style = typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(top = if (first) 0.dp else 6.dp)
        )

        PreviewKind.PARAGRAPH -> Text(shown, style = body)

        // A list item: the sign (or number) in the accent colour, then the text. A deeper level
        // is moved to the right. The text lines up under itself when it needs more than one line.
        PreviewKind.BULLET, PreviewKind.NUMBERED -> Row(
            modifier = Modifier.padding(start = (block.level * 20).dp),
            verticalAlignment = Alignment.Top
        ) {
            Text(
                block.label,
                style = body.copy(fontWeight = FontWeight.Bold),
                color = accent,
                modifier = Modifier
                    .widthIn(min = 24.dp)
                    .padding(end = 6.dp)
            )
            Text(shown, style = body, modifier = Modifier.weight(1f))
        }
    }
}

/**
 * The text of one block: bold words in bold letters, and every link in the link colour with an
 * underline that opens when it is tapped.
 */
private fun previewText(
    block: PreviewBlock,
    linkColor: Color,
    onOpenLink: (LinkSpan) -> Unit
): AnnotatedString = buildAnnotatedString {
    for (run in block.runs) {
        if (run.bold) pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
        append(run.text)
        if (run.bold) pop()
    }
    val linkStyles = TextLinkStyles(
        style = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
    )
    for (link in block.links) {
        // Never go past the text (the positions come from the same text, this is only a guard)
        if (link.start >= link.end || link.end > length) continue
        addLink(
            LinkAnnotation.Clickable(tag = link.target, styles = linkStyles) { onOpenLink(link) },
            link.start,
            link.end
        )
    }
}
