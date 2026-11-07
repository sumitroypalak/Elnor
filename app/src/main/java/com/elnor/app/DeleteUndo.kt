package com.elnor.app

import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Delete without a question: the note goes to the Trash at once, and a message with "Undo" is shown
 * at the bottom. The Trash is the safety net, so a confirm dialog is not needed. Undo takes the
 * notes out of the Trash again, exactly as they were (also pinned, if they were pinned).
 */

/** How long the "moved to Trash" message stays. Longer than the Archive message: a delete matters more. */
internal val DELETE_UNDO_DURATION = SnackbarDuration.Long

/** The text of the message. It says where the notes went, so the user knows they are not lost. */
internal fun deleteUndoMessage(count: Int): String =
    if (count == 1) "Note moved to Trash" else "$count notes moved to Trash"

/**
 * Shows the "moved to Trash" message with "Undo". Returns when the message is gone. If the user
 * taps Undo, [notes] are restored.
 *
 * [notes] must be the notes as they were BEFORE the delete (see [NoteRepository.restoreNotes]).
 * [moveJob] is the delete that is still running, if the caller started one: Undo waits for it, so
 * the restore can never run before the delete.
 *
 * The restore runs in [ApplicationScope], like every other change, so leaving the screen at the
 * same moment cannot cancel it half-way.
 */
internal suspend fun showDeleteUndo(
    snackbarHostState: SnackbarHostState,
    repository: NoteRepository,
    notes: List<Note>,
    moveJob: Job? = null
) {
    if (notes.isEmpty()) return
    // A newer message replaces an older one
    snackbarHostState.currentSnackbarData?.dismiss()
    val result = snackbarHostState.showSnackbar(
        message = deleteUndoMessage(notes.size),
        actionLabel = "Undo",
        withDismissAction = true,
        duration = DELETE_UNDO_DURATION
    )
    if (result == SnackbarResult.ActionPerformed) {
        ApplicationScope.scope.launch {
            moveJob?.join()
            repository.restoreNotes(notes)
        }
    }
}

/**
 * Moves [notes] to the Trash and offers Undo. [uiScope] must be a scope that stays alive while the
 * message is shown (one above the screen animation, not inside it).
 */
internal fun deleteNotesWithUndo(
    uiScope: CoroutineScope,
    snackbarHostState: SnackbarHostState,
    repository: NoteRepository,
    notes: List<Note>
) {
    if (notes.isEmpty()) return
    val ids = notes.map { it.id }
    val moveJob = ApplicationScope.scope.launch { repository.moveToTrashMany(ids) }
    uiScope.launch { showDeleteUndo(snackbarHostState, repository, notes, moveJob) }
}
