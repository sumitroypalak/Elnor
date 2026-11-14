package com.elnor.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch

// One note card of the home list / grid and its three-dot menu (split out of HomeScreen.kt, nothing
// changed in how it works).

/**
 * One card, used by the list and by the grid. [modifier] carries Modifier.animateItem() from the
 * lazy list / grid, so a note that is added, removed, pinned or re-sorted fades and slides instead
 * of jumping.
 */
@Composable
internal fun NoteListItem(
    note: Note,
    modifier: Modifier,
    dark: Boolean,
    cardColors: List<Color>,
    selected: Boolean,
    menuActions: List<NoteMenuAction>,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    // The color comes from the note's id, not from its place in the list,
    // so it stays the same when notes are added, pinned or searched
    val cardColor = if (dark) {
        MaterialTheme.colorScheme.surface
    } else {
        cardColors[cardColorIndex(note.id, cardColors.size)]
    }

    NoteCard(
        note = note,
        cardColor = cardColor,
        dark = dark,
        modifier = modifier,
        onClick = onClick,
        onLongClick = onLongClick,
        selected = selected,
        menuActions = menuActions
    )
}

/**
 * The three-dot menu of one note card. An archived note (a search result) can be unarchived or
 * deleted; a normal note can be pinned / unpinned, archived or deleted. [onArchive] and [onDelete]
 * are done by the home screen (they show the message with "Undo"); pin and unarchive go straight
 * to the [repository].
 */
internal fun noteMenuActions(
    note: Note,
    haptic: (HapticKind) -> Unit,
    repository: NoteRepository,
    onArchive: () -> Unit,
    onDelete: () -> Unit
): List<NoteMenuAction> {
    val deleteAction = NoteMenuAction(
        label = "Delete",
        isDestructive = true,
        onClick = onDelete
    )
    return if (note.archivedAt != null) {
        // An archived note (a search result) goes back to the list
        listOf(
            NoteMenuAction(
                label = "Unarchive",
                onClick = {
                    haptic(HapticKind.CONFIRM)
                    ApplicationScope.scope.launch {
                        repository.unarchiveMany(listOf(note.id))
                    }
                }
            ),
            deleteAction
        )
    } else {
        listOf(
            // Pinned notes are listed first. The list follows the
            // repository, so it reorders by itself.
            NoteMenuAction(
                label = if (note.pinnedAt != null) "Unpin" else "Pin",
                onClick = {
                    haptic(HapticKind.CONFIRM)
                    ApplicationScope.scope.launch {
                        repository.setPinned(note.id, note.pinnedAt == null)
                    }
                }
            ),
            NoteMenuAction(
                label = "Archive",
                onClick = onArchive
            ),
            deleteAction
        )
    }
}
