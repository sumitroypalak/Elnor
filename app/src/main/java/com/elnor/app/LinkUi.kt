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
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Shows the links of a note in the link colour with an underline. It only changes how the text
 * looks: the text itself, the cursor and the selection stay exactly as they are (the offsets are
 * the same before and after), so typing and saving are not affected.
 */
class LinkHighlightTransformation(
    private val links: List<LinkSpan>,
    private val color: Color
) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        if (links.isEmpty()) return TransformedText(text, OffsetMapping.Identity)
        val style = SpanStyle(color = color, textDecoration = TextDecoration.Underline)
        val styled = buildAnnotatedString {
            append(text)
            for (link in links) {
                // Never go past the text, in case the text changed since the links were calculated
                if (link.end <= text.length) addStyle(style, link.start, link.end)
            }
        }
        return TransformedText(styled, OffsetMapping.Identity)
    }
}

/**
 * The button that appears above the keyboard while the cursor is inside a link: Call, Email or
 * Open. It does not change the note.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkActionBar(link: LinkSpan, onClick: () -> Unit) {
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        AssistChip(
            onClick = onClick,
            label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            leadingIcon = {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        )
    }
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
