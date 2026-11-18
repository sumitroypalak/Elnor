package com.elnor.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

// The dialogs of the Collections feature (the rules are in Collections.kt).

/**
 * Asks for the name of a new collection ([exceptId] null) or a new name for [exceptId]. The Save
 * button is only on while the name is fine ([CollectionNames.problem]); the reason is shown under
 * the field once the user has typed something. [onSave] gets the cleaned name.
 */
@Composable
fun CollectionNameDialog(
    title: String,
    confirmLabel: String,
    initial: String,
    existing: List<NoteCollection>,
    exceptId: Long?,
    onSave: (name: String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    var typed by rememberSaveable { mutableStateOf(false) }
    val problem = CollectionNames.problem(text, existing, exceptId)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    typed = true
                },
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
                singleLine = true,
                placeholder = { Text("Collection name") },
                isError = typed && problem != null,
                supportingText = {
                    if (typed && problem != null) Text(CollectionNames.message(problem))
                }
            )
        },
        confirmButton = {
            TextButton(enabled = problem == null, onClick = { onSave(CollectionNames.clean(text)) }) {
                Text(confirmLabel)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

/**
 * "Move to collection": "No collection" and every collection, the one the note is in ([currentId])
 * marked. A tap on a row calls [onPick] (null for "No collection"). "New collection" calls [onNew].
 */
@Composable
fun CollectionChooserDialog(
    collections: List<NoteCollection>,
    currentId: Long?,
    onPick: (collectionId: Long?) -> Unit,
    onNew: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Move to collection") },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                item(key = "none") {
                    ChooserRow(label = "No collection", selected = currentId == null) { onPick(null) }
                }
                items(collections, key = { it.id }) { collection ->
                    ChooserRow(label = collection.name, selected = currentId == collection.id) {
                        onPick(collection.id)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onNew) { Text("New collection") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun ChooserRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

