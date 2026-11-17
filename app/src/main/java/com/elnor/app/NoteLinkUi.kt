@file:OptIn(ExperimentalMaterial3Api::class)

package com.elnor.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow

// The screens for links between notes ([[Name]], see NoteLinks.kt).

/**
 * The button above the keyboard while the cursor is in a link to another note: it opens that note.
 * It shares the row of the formatting buttons, like the Open / Call / Email button of web links.
 */
@Composable
fun NoteLinkChip(name: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    AssistChip(
        onClick = onClick,
        modifier = modifier,
        label = { Text("Go to $name", maxLines = 1, overflow = TextOverflow.Ellipsis) },
        leadingIcon = {
            Icon(
                Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    )
}

/**
 * "Link to a note": a search field and the list of notes that can be linked to. A tap on a note
 * calls [onPick] with the name to write in the link. [excludeId] is the note being edited.
 */
@Composable
fun NoteLinkPickerDialog(
    notes: List<Note>,
    excludeId: Long?,
    onPick: (name: String) -> Unit,
    onDismiss: () -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(notes, query, excludeId) { NoteLinks.suggestions(notes, query, excludeId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Link to a note") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("Search notes") }
                )
                Spacer(Modifier.height(8.dp))
                if (shown.isEmpty()) {
                    Text(
                        if (query.isBlank()) "There are no other notes to link to yet." else "No note found.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f),
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                        items(shown, key = { it.id }) { note ->
                            val name = NoteLinks.nameOf(note)
                            if (name != null) {
                                Text(
                                    name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onPick(name) }
                                        .padding(vertical = 12.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
