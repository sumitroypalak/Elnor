@file:OptIn(ExperimentalMaterial3Api::class)

package com.elnor.app

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight

/** The text at the top while notes are selected: "1 note selected" / "3 notes selected". */
internal fun selectionTitle(count: Int): String =
    if (count == 1) "1 note selected" else "$count notes selected"

/**
 * The top bar of the home screen while one or more notes are selected (touch and hold a note to
 * start). It replaces the normal bar: close the selection, [onTogglePin] (Pin, or Unpin when every
 * selected note is already pinned, see [allPinned]), [onArchive], [onDelete] and, in the
 * three-dot menu, [onSelectAll].
 */
@Composable
internal fun SelectionTopBar(
    count: Int,
    allPinned: Boolean,
    containerColor: Color,
    onClose: () -> Unit,
    onTogglePin: () -> Unit,
    onArchive: () -> Unit,
    onDelete: () -> Unit,
    onSelectAll: () -> Unit
) {
    TopAppBar(
        title = {
            Text(selectionTitle(count), fontWeight = FontWeight.Bold, maxLines = 1)
        },
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Cancel selection")
            }
        },
        actions = {
            TextButton(onClick = onTogglePin) { Text(if (allPinned) "Unpin" else "Pin") }
            TextButton(onClick = onArchive) { Text("Archive") }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete selected notes",
                    tint = MaterialTheme.colorScheme.error
                )
            }
            var menuOpen by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "More options")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Select all") },
                        onClick = {
                            menuOpen = false
                            onSelectAll()
                        }
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = containerColor,
            scrolledContainerColor = containerColor
        )
    )
}
