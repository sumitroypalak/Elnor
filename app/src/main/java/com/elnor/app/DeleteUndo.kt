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
 * The notes of the "moved to Trash" message that is on the screen now, so that a second delete
 * made while the message is still there can join it. Before, the second message replaced the
 * first one and the first delete lost its Undo for good.
 */
private class DeleteUndoBatch(
    val host: SnackbarHostState,
    var notes: List<Note>,
    var moveJobs: List<Job>
) {
    /** The text of the message that shows this batch now. Tells it apart from a message of another kind. */
    var shownMessage: String = ""

    /** Grows with every message of this batch. Only the newest message ends the batch. */
    var generation: Int = 0
}

/** The batch that has its message on the screen now, or null. Only used from the main thread. */
private var activeBatch: DeleteUndoBatch? = null

/**
 * Shows the "moved to Trash" message with "Undo". Returns when the message is gone. If the user
 * taps Undo, [notes] are restored.
 *
 * Deleting again while the message is still there does not replace it: the new notes join the
 * same message ("2 notes moved to Trash") and one Undo brings all of them back. The earlier call
 * of this function then simply returns.
 *
 * [notes] must be the notes as they were BEFORE the delete (see [NoteRepository.restoreNotes]).
 * [moveJob] is the delete that is still running, if the caller started one: Undo waits for it, so
 * the restore can never run before the delete.
 *
 * The restore runs in [restoreScope] ([ApplicationScope] in the app), like every other change, so
 * leaving the screen at the same moment cannot cancel it half-way.
 *
 * Must be called from the main thread, like the rest of the screen code.
 */
internal suspend fun showDeleteUndo(
    snackbarHostState: SnackbarHostState,
    repository: NoteRepository,
    notes: List<Note>,
    moveJob: Job? = null,
    restoreScope: CoroutineScope = ApplicationScope.scope
) {
    if (notes.isEmpty()) return

    // Join the message that is on the screen now, but only if it is really a "moved to Trash"
    // message of the same host. Another kind of message (Archive, a task) may have replaced it.
    val earlier = activeBatch?.takeIf {
        it.host === snackbarHostState &&
            snackbarHostState.currentSnackbarData?.visuals?.message == it.shownMessage
    }
    val batch = if (earlier != null) {
        earlier.notes = (earlier.notes + notes).distinctBy { it.id }
        earlier.moveJobs = earlier.moveJobs + listOfNotNull(moveJob)
        earlier
    } else {
        DeleteUndoBatch(snackbarHostState, notes, listOfNotNull(moveJob))
    }
    activeBatch = batch
    val generation = ++batch.generation
    // What this message offers to restore. Later deletes make a newer message with a longer list.
    val shownNotes = batch.notes
    val shownJobs = batch.moveJobs
    batch.shownMessage = deleteUndoMessage(shownNotes.size)

    // A newer message replaces an older one (the joined one, or one of another kind)
    snackbarHostState.currentSnackbarData?.dismiss()
    val result = try {
        snackbarHostState.showSnackbar(
            message = batch.shownMessage,
            actionLabel = "Undo",
            withDismissAction = true,
            duration = DELETE_UNDO_DURATION
        )
    } finally {
        // Only the newest message of a batch ends it. An older one that was replaced by the
        // joined message must leave the batch alone.
        if (batch.generation == generation && activeBatch === batch) activeBatch = null
    }
    if (result == SnackbarResult.ActionPerformed) {
        restoreScope.launch {
            shownJobs.forEach { it.join() }
            repository.restoreNotes(shownNotes)
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
