package com.drafto.app

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Where notes are stored. The editor ViewModel only knows this interface, so tests can use
 * a fake and the storage can be changed later (for example to Room) without touching the
 * save logic. The three suspend functions match what a Room DAO offers.
 */
interface NoteRepository {
    /** All notes, newest first. The UI reads this, so the list updates by itself after a save. */
    val notes: StateFlow<List<Note>>

    /** Stores a new note and returns the id it got. The id inside [note] is ignored. */
    suspend fun insert(note: Note): Long

    /** Replaces the stored note that has the same id. Does nothing if it no longer exists. */
    suspend fun update(note: Note)

    /** Removes the note. Does nothing if it no longer exists. */
    suspend fun delete(id: Long)
}

/**
 * Lives as long as the app process, so work started here is not cancelled when a screen or a
 * ViewModel goes away. Used for saving notes. See NoteEditorViewModel for why.
 */
object ApplicationScope {
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

/**
 * Keeps the notes in SharedPreferences ("drafto_notes" / "notes"), exactly like the old code,
 * so notes the user already has are still there.
 */
class PrefsNoteRepository private constructor(context: Context) : NoteRepository {

    private val prefs = context.getSharedPreferences("drafto_notes", Context.MODE_PRIVATE)
    private val lock = Any()

    private val _notes = MutableStateFlow(readAll())
    override val notes: StateFlow<List<Note>> = _notes.asStateFlow()

    override suspend fun insert(note: Note): Long = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val current = _notes.value
            // Ids stay time based like before, and are always bigger than every existing id.
            val id = maxOf(System.currentTimeMillis(), (current.maxOfOrNull { it.id } ?: 0L) + 1)
            writeAll(current + note.copy(id = id))
            id
        }
    }

    override suspend fun update(note: Note) {
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                writeAll(_notes.value.map { if (it.id == note.id) note else it })
            }
        }
    }

    override suspend fun delete(id: Long) {
        withContext(Dispatchers.IO) {
            synchronized(lock) {
                writeAll(_notes.value.filter { it.id != id })
            }
        }
    }

    private fun readAll(): List<Note> {
        return try {
            val raw = prefs.getString("notes", "[]") ?: "[]"
            val array = JSONArray(raw)
            List(array.length()) { i ->
                val obj = array.getJSONObject(i)
                Note(
                    id = obj.getLong("id"),
                    title = obj.getString("title"),
                    body = obj.getString("body"),
                    updatedAt = obj.getLong("updatedAt")
                )
            }.sortedByDescending { it.updatedAt }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun writeAll(list: List<Note>) {
        val sorted = list.sortedByDescending { it.updatedAt }
        val array = JSONArray()
        sorted.forEach { note ->
            array.put(JSONObject().apply {
                put("id", note.id)
                put("title", note.title)
                put("body", note.body)
                put("updatedAt", note.updatedAt)
            })
        }
        // commit() (not apply()) so the data is on disk when this function returns.
        // We are already on a background thread here.
        prefs.edit().putString("notes", array.toString()).commit()
        _notes.value = sorted
    }

    companion object {
        @Volatile
        private var instance: PrefsNoteRepository? = null

        fun get(context: Context): PrefsNoteRepository {
            return instance ?: synchronized(this) {
                instance ?: PrefsNoteRepository(context.applicationContext).also { instance = it }
            }
        }
    }
}
