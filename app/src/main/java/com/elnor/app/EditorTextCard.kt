@file:OptIn(ExperimentalMaterial3Api::class)

package com.elnor.app

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp

// The card with the title and the text of the note (split out of EditorScreen.kt, nothing changed
// in how it works).

/**
 * The title and the text sit on one rounded card, in the same shape as the note cards of the home
 * screen. It fills the rest of the screen.
 *
 * It only draws the two fields. The page ([EditorScreen]) keeps the text and does the work in
 * [onTitleChange] and [onBodyChange] (for example telling the ViewModel, so it is saved after a
 * typing pause).
 *
 * With [previewMode] the card shows the note as a reading page instead of the two fields: the title
 * as a heading and the text without the formatting marks (see [MarkdownPreviewView]). Nothing can
 * be typed there; a tap on a link calls [onOpenLink].
 */
@Composable
internal fun ColumnScope.EditorTextCard(
    dark: Boolean,
    title: String,
    onTitleChange: (String) -> Unit,
    lastEdited: String?,
    bodyValue: TextFieldValue,
    onBodyChange: (TextFieldValue) -> Unit,
    bodyFocus: FocusRequester,
    onBodyFocusChange: (Boolean) -> Unit,
    textStyling: NoteTextTransformation,
    previewMode: Boolean,
    onOpenLink: (LinkSpan) -> Unit,
    onToggleCheck: (lineIndex: Int) -> Unit,
    /** null: note links are not followed on this page (they are shown as normal text). */
    onOpenNoteLink: ((name: String) -> Unit)?
) {
    val fieldColors = TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .padding(top = 4.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surface,
        // A white card on the light page needs a thin edge (the dark card does not)
        border = if (dark) null else BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 8.dp)
        ) {
            if (previewMode) {
                // The title as a heading (nothing at all when the note has no title)
                if (title.isNotBlank()) {
                    Text(
                        title,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            } else {
                // Title (optional)
                TextField(
                    value = title,
                    onValueChange = onTitleChange,
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    textStyle = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    placeholder = {
                        Text(
                            "Title",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f)
                        )
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    keyboardActions = KeyboardActions(
                        onNext = { bodyFocus.requestFocus() }
                    ),
                    colors = fieldColors
                )
            }

            // When the note was last saved (nothing for a note that was never saved)
            if (lastEdited != null) {
                Text(
                    lastEdited,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
            }

            if (previewMode) {
                // The reading page: fills the rest of the card
                MarkdownPreviewView(
                    text = bodyValue.text,
                    onOpenLink = onOpenLink,
                    onToggleCheck = onToggleCheck,
                    onOpenNoteLink = onOpenNoteLink,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )
            } else {
                // Body: fills the rest of the card
                TextField(
                    value = bodyValue,
                    onValueChange = onBodyChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .focusRequester(bodyFocus)
                        .onFocusChanged { onBodyFocusChange(it.isFocused) },
                    visualTransformation = textStyling,
                    textStyle = MaterialTheme.typography.bodyLarge,
                    placeholder = {
                        Text(
                            "Start writing...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.35f)
                        )
                    },
                    colors = fieldColors
                )
            }
        }
    }
}
