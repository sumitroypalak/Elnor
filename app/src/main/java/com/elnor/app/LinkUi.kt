package com.elnor.app

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Email
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em

// "Find in note" colours. The text colour is fixed dark so it can be read on the yellow in the
// dark theme too.
private val FOUND_STYLE = SpanStyle(background = Color(0xFFFFE082), color = Color(0xFF1B1B1B))
private val FOUND_CURRENT_STYLE = SpanStyle(background = Color(0xFFFFA726), color = Color(0xFF1B1B1B))

/**
 * Shows the text of a note styled while it is edited: the links in the link colour with an
 * underline, and the formatting ([MarkdownFormat]): bold words, bigger headings, and the marks
 * (** # - 1.) dimmed. It only changes how the text looks: the text itself, the cursor and the
 * selection stay exactly as they are (the offsets are the same before and after), so typing and
 * saving are not affected.
 */
class NoteTextTransformation(
    private val links: List<LinkSpan>,
    private val format: List<MarkdownFormat.Span>,
    private val linkColor: Color,
    private val markerColor: Color,
    /** The places found by "Find in note": coloured with a background. Empty when Find is closed. */
    private val found: List<FindInNote.Match> = emptyList(),
    /** Which of [found] is the chosen one (a stronger colour), or -1. */
    private val foundCurrent: Int = -1
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (links.isEmpty() && format.isEmpty() && found.isEmpty()) {
            return TransformedText(text, OffsetMapping.Identity)
        }
        val linkStyle = SpanStyle(color = linkColor, textDecoration = TextDecoration.Underline)
        val styled = buildAnnotatedString {
            append(text)
            // Never go past the text, in case the text changed since the spans were calculated
            for (span in format) {
                if (span.start >= span.end || span.end > text.length) continue
                addStyle(formatStyle(span.kind), span.start, span.end)
            }
            for (link in links) {
                if (link.end <= text.length) addStyle(linkStyle, link.start, link.end)
            }
            // Last, so the found places stay visible over bold, links and the rest
            for ((index, match) in found.withIndex()) {
                if (match.start >= match.end || match.end > text.length) continue
                addStyle(if (index == foundCurrent) FOUND_CURRENT_STYLE else FOUND_STYLE, match.start, match.end)
            }
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }

    // Headings stay a little smaller than the line height of the text, so lines never overlap.
    // The sizes are in "em" (times the size of the text around them), not in sp, so they grow and
    // shrink with the font size of Settings. At the normal size 1.375 em is 22 sp and 1.1875 em is
    // 19 sp (the text is 16 sp, its line 24 sp).
    private fun formatStyle(kind: MarkdownFormat.Kind): SpanStyle = when (kind) {
        MarkdownFormat.Kind.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
        MarkdownFormat.Kind.HEADING1 -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = 1.375.em)
        MarkdownFormat.Kind.HEADING2 -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = 1.1875.em)
        MarkdownFormat.Kind.MARKER -> SpanStyle(color = markerColor)
        MarkdownFormat.Kind.LIST_MARKER -> SpanStyle(color = linkColor, fontWeight = FontWeight.Bold)
    }
}

/**
 * The button that appears above the keyboard while the cursor is inside a link: Call, Email or
 * Open. It does not change the note. The editor puts it in the row of the formatting buttons.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkActionChip(link: LinkSpan, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val label = when (link.type) {
        LinkType.PHONE -> "Call ${link.text}"
        LinkType.EMAIL -> "Email ${link.text}"
        LinkType.WEB -> "Open ${link.text}"
    }
    val icon = when (link.type) {
        LinkType.PHONE -> Icons.Default.Call
        LinkType.EMAIL -> Icons.Default.Email
        LinkType.WEB -> Icons.AutoMirrored.Filled.ArrowForward
    }
    AssistChip(
        onClick = onClick,
        modifier = modifier,
        label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        }
    )
}

/**
 * Hands the link to Android: the dialer for a phone number (it only fills in the number, the
 * user presses call, so no phone permission is needed), the mail app for an address, the browser
 * for a web link. A phone with no app for it gets a message instead of a crash.
 */
fun openLink(context: Context, link: LinkSpan) {
    val action = if (link.type == LinkType.PHONE) Intent.ACTION_DIAL else Intent.ACTION_VIEW
    val intent = Intent(action, Uri.parse(link.target)).apply {
        // Only needed when the context is not an Activity
        if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    val started = runCatching { context.startActivity(intent) }.isSuccess
    if (!started) {
        val message = when (link.type) {
            LinkType.PHONE -> "No app to make a call"
            LinkType.EMAIL -> "No e-mail app found"
            LinkType.WEB -> "No browser found"
        }
        Toast.makeText(context.applicationContext, message, Toast.LENGTH_SHORT).show()
    }
}
