@file:OptIn(ExperimentalMaterial3Api::class)

package com.elnor.app

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

// The top bar of the home screen (split out of HomeScreen.kt, nothing changed in how it works).

/**
 * The normal bar (title and menu button) and the bar of the selection. They fade into each other.
 *
 * The bar only draws and reports clicks. The home screen ([ElnorApp]) owns the selection and does
 * the work in [onClearSelection], [onSelectAll], [onArchiveNotes] and [onDeleteNotes]. Pin and
 * Unarchive are done here, straight on the [repository], as before.
 */
@Composable
internal fun HomeTopBar(
    selectionMode: Boolean,
    selectedIds: Set<Long>,
    everyNote: List<Note>,
    shownNotes: List<Note>,
    bgColor: Color,
    repository: NoteRepository,
    onMenuClick: () -> Unit,
    onClearSelection: () -> Unit,
    onSelectAll: (Set<Long>) -> Unit,
    onArchiveNotes: (List<Long>) -> Unit,
    onDeleteNotes: (List<Note>) -> Unit
) {
    val haptic = rememberHaptic()
    // The normal bar and the bar of the selection fade into each other. The number of
    // selected notes is remembered, so the bar that is fading out does not show "0".
    val lastSelectedCount = remember { intArrayOf(0) }
    if (selectionMode) lastSelectedCount[0] = selectedIds.size
    Crossfade(
        targetState = selectionMode,
        animationSpec = Motion.short(),
        label = "topBar"
    ) { selecting ->
        if (selecting) {
            // The selected notes. An archived note (a search result) cannot be pinned, and
            // "Archive" does nothing for it, so those actions only look at the other notes.
            val selectedNotes = everyNote.filter { it.id in selectedIds }
            val pinTargets = selectedNotes.filter { it.archivedAt == null }
            val allArchived = selectedNotes.isNotEmpty() && pinTargets.isEmpty()
            SelectionTopBar(
                count = lastSelectedCount[0],
                canPin = pinTargets.isNotEmpty(),
                allPinned = pinTargets.isNotEmpty() && pinTargets.all { it.pinnedAt != null },
                allArchived = allArchived,
                containerColor = bgColor,
                onClose = onClearSelection,
                onTogglePin = {
                    val ids = pinTargets.map { it.id }
                    // all pinned -> unpin them all, otherwise pin them all
                    val pin = !pinTargets.all { it.pinnedAt != null }
                    haptic(HapticKind.CONFIRM)
                    ApplicationScope.scope.launch { repository.setPinnedMany(ids, pin) }
                    onClearSelection()
                },
                onArchive = {
                    onClearSelection()
                    if (allArchived) {
                        val ids = selectedNotes.map { it.id }
                        haptic(HapticKind.CONFIRM)
                        ApplicationScope.scope.launch { repository.unarchiveMany(ids) }
                    } else {
                        onArchiveNotes(pinTargets.map { it.id })
                    }
                },
                onDelete = {
                    // The notes as they are now (with their pin), then the selection ends
                    val targets = selectedNotes
                    onClearSelection()
                    onDeleteNotes(targets)
                },
                onSelectAll = {
                    haptic(HapticKind.TICK)
                    onSelectAll(shownNotes.mapTo(HashSet()) { it.id })
                }
            )
        } else {
            // A normal-height bar: the title and the menu button share one line, so there
            // is no empty space above the title. The 4.dp / 16.dp paddings put the title
            // and the menu button on the same 20.dp edges as the tagline and the search bar.
            TopAppBar(
                title = {
                    Text(
                        "Elnor",
                        modifier = Modifier.padding(start = 4.dp),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                actions = {
                    // Hamburger button: opens the side menu (Settings, Trash)
                    FilledTonalIconButton(
                        onClick = onMenuClick,
                        modifier = Modifier.padding(end = 16.dp)
                    ) {
                        Icon(Icons.Default.Menu, contentDescription = "Open menu")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = bgColor,
                    scrolledContainerColor = bgColor
                )
            )
        }
    }
}
