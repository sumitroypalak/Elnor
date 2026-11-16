@file:OptIn(ExperimentalMaterial3Api::class)

package com.elnor.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

// ------------- Select many notes in the Trash and in the Archive ------------------

/**
 * Touch and hold a note in the Trash or in the Archive to start selecting, then tap more notes.
 * This file holds what both screens share: the top bar, the toggle rule and the messages.
 */

/** Adds [id] to the selection, or takes it out when it is already selected. */
internal fun toggleSelection(selected: Set<Long>, id: Long): Set<Long> =
    if (id in selected) selected - id else selected + id

/** The message after notes were taken out of the Trash. */
internal fun restoredMessage(count: Int): String =
    if (count == 1) "Note restored" else "$count notes restored"

/** The message after notes were taken out of the Archive. */
internal fun unarchivedMessage(count: Int): String =
    if (count == 1) "Note unarchived" else "$count notes unarchived"

/** The text of the "Delete forever?" question for [count] selected notes in the Trash. */
internal fun deleteForeverQuestion(count: Int): String =
    if (count == 1) {
        "This note will be permanently deleted. This cannot be undone."
    } else {
        "These $count notes will be permanently deleted. This cannot be undone."
    }

/** A short message without an action (no Undo). A newer message replaces the one that is shown. */
internal fun showPlainMessage(
    scope: CoroutineScope,
    host: SnackbarHostState,
    message: String
) {
    scope.launch {
        host.currentSnackbarData?.dismiss()
        host.showSnackbar(message = message, duration = SnackbarDuration.Short)
    }
}

/**
 * The top bar of the Trash and Archive screens while notes are selected. It replaces the normal
 * bar: close the selection, the screen's own buttons in [actions], and "Select all" in the
 * three-dot menu. Same look as [SelectionTopBar] of the home screen.
 */
@Composable
internal fun BulkSelectionTopBar(
    count: Int,
    containerColor: Color,
    onClose: () -> Unit,
    onSelectAll: () -> Unit,
    actions: @Composable RowScope.() -> Unit
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
            actions()
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
