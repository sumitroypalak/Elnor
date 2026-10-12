package com.elnor.app

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

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

    /** Takes the note out of the Trash, exactly as it was. Does nothing if it is not there. */
    suspend fun restore(id: Long)

    /** Removes the note for good. Only for notes that the user deletes from the Trash. */
    suspend fun deletePermanently(id: Long)

    /** Removes every note in the Trash for good. */
    suspend fun emptyTrash()
}

/**
 * Lives as long as the app process, so work started here is not cancelled when a screen or a
 * ViewModel goes away. Used for saving notes. See NoteEditorViewModel for why.
 */
object ApplicationScope {
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
