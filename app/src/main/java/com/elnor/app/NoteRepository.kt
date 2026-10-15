package com.elnor.app

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Where notes are stored. The editor ViewModel only knows this interface, so tests can use
 * a fake and the storage can be changed without touching the save logic.
 */
interface NoteRepository {
    /**
     * All notes that are not in the Trash, newest first. The UI reads this, so the list updates
     * by itself after a save.
     */
    val notes: StateFlow<List<Note>>

    /** Notes in the Trash, the most recently deleted first. Updates by itself. */
    val trashedNotes: Flow<List<Note>>

    /**
     * Notes (not in the Trash) that contain [query] in the title or body, newest first.
     * Upper/lower case, and different ways of writing the same letter, do not matter. A blank
     * query gives all notes. % _ and \ in [query] are searched as normal characters.
     */
    fun search(query: String): Flow<List<Note>>

    /** Stores a new note and returns the id it got. The id inside [note] is ignored. */
    suspend fun insert(note: Note): Long

    /**
     * Changes the title and body of the stored note that has the same id. Does nothing if it no
     * longer exists. It never moves a note in or out of the Trash.
     */
    suspend fun update(note: Note)

    /**
     * "Delete" for the user: the note goes to the Trash and keeps all its data. Does nothing if
     * it no longer exists or is already in the Trash.
     */
    suspend fun moveToTrash(id: Long)

    /**
     * Pins ([pinned] = true) or unpins a note. Pinned notes are listed first. Pinning a pinned
     * note, or a note in the Trash, does nothing. The text and updatedAt are not changed.
     */
    suspend fun setPinned(id: Long, pinned: Boolean)

    /** Takes the note out of the Trash, exactly as it was. Does nothing if it is not there. */
    suspend fun restore(id: Long)

    /** Removes the note for good. Only for notes that the user deletes from the Trash. */
    suspend fun deletePermanently(id: Long)

    /** Removes every note in the Trash for good. */
    suspend fun emptyTrash()

    /**
     * Removes for good every note that has been in the Trash for [TRASH_RETENTION_DAYS] days or
     * longer, counted from the time it was moved there (its deletedAt). Notes that are not in
     * the Trash are never touched. Returns how many notes were removed.
     */
    suspend fun purgeExpiredTrash(): Int
}

/** How long a note stays in the Trash. After this many days it is deleted for good. */
const val TRASH_RETENTION_DAYS = 30

/**
 * [TRASH_RETENTION_DAYS] in milliseconds (30 x 24 hours). deletedAt is a point in time
 * (milliseconds since 1970), so the count keeps going while the app is closed.
 */
const val TRASH_RETENTION_MS = TRASH_RETENTION_DAYS * 24L * 60L * 60L * 1000L

/**
 * Starts [NoteRepository.purgeExpiredTrash] in the background. It runs in [ApplicationScope],
 * like saves do, so leaving a screen cannot cancel it half-way. A failure is only logged:
 * cleaning the Trash must never crash the app.
 */
fun NoteRepository.purgeExpiredTrashInBackground() {
    ApplicationScope.scope.launch {
        try {
            purgeExpiredTrash()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("NoteRepository", "Cleaning the Trash failed", e)
        }
    }
}

/**
 * Lives as long as the app process, so work started here is not cancelled when a screen or a
 * ViewModel goes away. Used for saving notes. See NoteEditorViewModel for why.
 */
object ApplicationScope {
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
