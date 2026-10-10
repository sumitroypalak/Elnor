package com.drafto.app

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

/**
 * Text used to check fonts. Written with \u escapes so no editor can change it:
 *
 *   বাংলা ক্ষমা দ্ধ স্ট্র English 😀 👨‍👩‍👧 ✓ → ♥ こんにちは مرحبا
 *
 * Bengali (with ই-কার/আ-কার signs and conjuncts ক্ষ, দ্ধ, স্ট্র), English, an emoji, a family
 * emoji (3 emoji joined with ZWJ), symbols, Japanese and Arabic.
 */
private const val FONT_AUDIT_TEXT =
    "\u09AC\u09BE\u0982\u09B2\u09BE " + // বাংলা
        "\u0995\u09CD\u09B7\u09AE\u09BE " + // ক্ষমা
        "\u09A6\u09CD\u09A7 " + // দ্ধ
        "\u09B8\u09CD\u099F\u09CD\u09B0 " + // স্ট্র
        "English " +
        "\uD83D\uDE00 " + // 😀
        "\uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67 " + // 👨‍👩‍👧
        "\u2713 \u2192 \u2665 " + // ✓ → ♥
        "\u3053\u3093\u306B\u3061\u306F " + // こんにちは
        "\u0645\u0631\u062D\u0628\u0627" // مرحبا

// ---- The four places where the app shows text, with the same styles as the real screens ----

@Composable
private fun FontAuditScreens(dark: Boolean) {
    val fieldColors = TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent
    )
    val cardColor = if (dark) MaterialTheme.colorScheme.surface else Color(0xFFE9E5FF)

    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AuditLabel("1. Title field (editor)")
        TextField(
            value = FONT_AUDIT_TEXT,
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            textStyle = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
            colors = fieldColors
        )

        AuditLabel("2. Body field (editor)")
        TextField(
            value = "$FONT_AUDIT_TEXT\n$FONT_AUDIT_TEXT",
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            textStyle = MaterialTheme.typography.bodyLarge,
            colors = fieldColors
        )

        AuditLabel("3. Search bar")
        OutlinedTextField(
            value = FONT_AUDIT_TEXT,
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            textStyle = MaterialTheme.typography.bodyLarge,
            shape = CircleShape
        )

        AuditLabel("4a. Note in the list (with a title)")
        NoteCard(
            note = Note(1L, FONT_AUDIT_TEXT, "$FONT_AUDIT_TEXT\n$FONT_AUDIT_TEXT", 0L),
            cardColor = cardColor,
            dark = dark,
            onClick = {}
        )

        AuditLabel("4b. Note in the list (no title: the first line is the name)")
        NoteCard(
            note = Note(2L, "", "$FONT_AUDIT_TEXT\n$FONT_AUDIT_TEXT\n$FONT_AUDIT_TEXT", 0L),
            cardColor = cardColor,
            dark = dark,
            onClick = {}
        )
    }
}

@Composable
private fun AuditLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary
    )
}

// ---- Every text style the app uses. The red area is the line box of the text. ----

@Composable
private fun FontAuditAllStyles() {
    val typography = MaterialTheme.typography
    val styles: List<Pair<String, TextStyle>> = listOf(
        "headlineMedium (top bar)" to typography.headlineMedium,
        "headlineSmall (editor title)" to typography.headlineSmall,
        "titleLarge (note name, headings)" to typography.titleLarge,
        "titleMedium" to typography.titleMedium,
        "titleSmall" to typography.titleSmall,
        "bodyLarge (editor, search)" to typography.bodyLarge,
        "bodyMedium (note preview)" to typography.bodyMedium,
        "bodySmall" to typography.bodySmall,
        "labelLarge" to typography.labelLarge,
        "labelMedium" to typography.labelMedium,
        "labelSmall (date)" to typography.labelSmall
    )

    Column(
        modifier = Modifier
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        for ((name, style) in styles) {
            AuditLabel(name)
            // If a letter sticks out of the red area, the line height is too small.
            Text(
                FONT_AUDIT_TEXT,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0x22FF0000)),
                style = style,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

// ---- The previews ----

@Preview(name = "Screens - light", showBackground = true, widthDp = 380, heightDp = 1250)
@Composable
private fun FontAuditLight() {
    DraftoTheme(darkTheme = false) { FontAuditScreens(dark = false) }
}

@Preview(
    name = "Screens - dark",
    showBackground = true,
    widthDp = 380,
    heightDp = 1250,
    uiMode = Configuration.UI_MODE_NIGHT_YES
)
@Composable
private fun FontAuditDark() {
    DraftoTheme(darkTheme = true) { FontAuditScreens(dark = true) }
}

// Many people use a bigger system font. Clipping shows up here first.
@Preview(name = "Screens - font size 1.3x", showBackground = true, widthDp = 380, heightDp = 1750, fontScale = 1.3f)
@Composable
private fun FontAuditLargeFont() {
    DraftoTheme(darkTheme = false) { FontAuditScreens(dark = false) }
}

@Preview(name = "All text styles", showBackground = true, widthDp = 380, heightDp = 2300)
@Composable
private fun FontAuditStyles() {
    DraftoTheme(darkTheme = false) { FontAuditAllStyles() }
}
