@file:OptIn(ExperimentalMaterial3Api::class)

package com.elnor.app

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight

/**
 * Asked when the user leaves a note whose last save did not work (split out of EditorScreen.kt,
 * nothing changed in how it works). [onKeepEditing] also runs when the dialog is dismissed: the
 * page then tries to save again. [onLeave] closes the page after one last try.
 */
@Composable
internal fun UnsavedNoteDialog(
    onKeepEditing: () -> Unit,
    onLeave: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onKeepEditing,
        title = { Text("Note not saved", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "Elnor could not save this note. If you leave now, your latest changes " +
                    "may be lost. Use Copy text in the menu to keep the text first."
            )
        },
        confirmButton = {
            TextButton(onClick = onLeave) {
                Text("Leave anyway", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onKeepEditing) {
                Text("Keep editing")
            }
        }
    )
}
