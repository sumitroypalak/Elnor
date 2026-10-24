package com.elnor.app

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The default of [NoteRepository.notesLoaded] for a repository that has its notes at once. */
private val ALWAYS_LOADED: StateFlow<Boolean> = MutableStateFlow(true).asStateFlow()

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

    /**
     * False until [notes] has its first real value from storage. [notes] starts as an empty
     * list, so the screen uses this to show a loading state instead of "Your space is empty".
     * A fake repository that has its notes at once can leave the default (always true).
     */
    val notesLoaded: StateFlow<Boolean> get() = ALWAYS_LOADED

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
     * Like [insert], but the new note gets the id that is inside [note] (a new note chooses its id
     * when its editor page opens). Returns that id. A repository that cannot do this may ignore
     * the id; then it is the same as [insert].
     */
    suspend fun insertWithId(note: Note): Long = insert(note)

    /**
     * Like [update], but tells if the note exists: false when no stored note has this id (nothing
     * was written). Never moves a note in or out of the Trash.
     */
    suspend fun updateIfExists(note: Note): Boolean {
        update(note)
        return true
    }

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

    /** Every note for the backup file: normal notes and notes in the Trash. */
    suspend fun exportAll(): List<Note>

    /**
     * Adds the notes of a backup. It only adds: notes that are already stored (same title, text
     * and updatedAt) are skipped, and nothing that exists is changed or removed. The ids in
     * [notes] are ignored, every added note gets a new id. pinnedAt and deletedAt are kept, so a
     * note that was in the Trash stays there until its 30 days are over.
     */
    suspend fun importNotes(notes: List<Note>): ImportSummary

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
