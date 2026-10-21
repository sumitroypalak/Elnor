@file:OptIn(ExperimentalMaterial3Api::class)

package com.elnor.app

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ----------------------------- Note card -----------------------------------

/**
 * One entry of the three-dot menu of a note. To add Pin, Archive and so on later, pass one more
 * entry in the list of [NoteCard]. Nothing else has to change.
 */
data class NoteMenuAction(
    val label: String,
    val isDestructive: Boolean = false,
    val onClick: () -> Unit
)

/**
 * One note in the list. Also used by FontAuditPreview.
 * [menuActions] are the entries of the three-dot menu in the top right corner of the card.
 * With no entries (the default) the menu button is not shown.
 */
@Composable
fun NoteCard(
    note: Note,
    cardColor: Color,
    dark: Boolean,
    onClick: () -> Unit,
    menuActions: List<NoteMenuAction> = emptyList()
) {
    val hasMenu = menuActions.isNotEmpty()

    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(
            containerColor = cardColor
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 0.dp
        )
    ) {
        // fillMaxWidth: the Box is always as wide as the card, not only as wide as the text.
        // Then the menu button below (TopEnd) stays at the same place for every title length.
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    getDisplayTitle(note),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (dark) MaterialTheme.colorScheme.onSurface
                    else Color(0xFF24243A),
                    // keeps the title out from under the menu button
                    modifier = if (hasMenu) Modifier.padding(end = 36.dp) else Modifier,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val preview = getPreviewBody(note)
                if (preview.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        preview,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (dark) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                        else Color(0xFF454559),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(14.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // A small mark on a pinned note
                    if (note.pinnedAt != null) {
                        Text("\uD83D\uDCCC", fontSize = 12.sp)
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        formatNoteDate(note.updatedAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (dark) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                        else Color(0xFF626278)
                    )
                }
            }

            if (hasMenu) {
                NoteOverflowMenu(
                    actions = menuActions,
                    dark = dark,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 12.dp, end = 4.dp)
                )
            }
        }
    }
}

/** The three-dot button of a note and the popup menu it opens. Every entry of [actions] is one item. */
@Composable
private fun NoteOverflowMenu(
    actions: List<NoteMenuAction>,
    dark: Boolean,
    modifier: Modifier = Modifier
) {
    // Every note card has its own state, so each menu opens and closes on its own
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        IconButton(onClick = { expanded = true }) {
            Icon(
                Icons.Default.MoreVert,
                contentDescription = "More options",
                tint = if (dark) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                else Color(0xFF454559)
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            for (action in actions) {
                DropdownMenuItem(
                    text = {
                        Text(
                            action.label,
                            color = if (action.isDestructive) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface
                        )
                    },
                    onClick = {
                        expanded = false
                        action.onClick()
                    }
                )
            }
        }
    }
}

/**
 * "Delete note?" The note goes to the Trash, where it can be restored, so this is the same
 * question in the editor and in the notes list.
 */
@Composable
internal fun DeleteNoteDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete note?", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "This note will be moved to Trash. You can restore it within " +
                    "$TRASH_RETENTION_DAYS days. After that it is deleted for good."
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Delete", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
