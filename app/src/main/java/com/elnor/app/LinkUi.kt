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
import androidx.compose.ui.unit.sp

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
    private val markerColor: Color
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (links.isEmpty() && format.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
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
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }

    // Headings stay a little smaller than the 24.sp line height of the text, so lines never overlap
    private fun formatStyle(kind: MarkdownFormat.Kind): SpanStyle = when (kind) {
        MarkdownFormat.Kind.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
        MarkdownFormat.Kind.HEADING1 -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp)
        MarkdownFormat.Kind.HEADING2 -> SpanStyle(fontWeight = FontWeight.Bold, fontSize = 19.sp)
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
