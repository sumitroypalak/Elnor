package com.elnor.app

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Auto-save for the note editor. There is no Save button.
 *
 * The text is only kept in memory while the user types (no save on every key).
 * It is written to the [repository] only when:
 *  - [onBack]: the back arrow or the system back was pressed
 *  - [onStop]: the app went to the background (Lifecycle ON_STOP)
 *
 * Rules:
 *  - A new note whose title and body are both blank is never saved.
 *  - The first save of a new note is an insert. The id that comes back is kept in the
 *    session, so every later save of that note is an update. No second copy is created.
 *  - An existing note that was changed is updated and gets a new updatedAt.
 *    If nothing changed, it is not touched.
 *  - An existing note whose title and body were both cleared is deleted.
 *
 * Why saves run in [saveScope] (an application-level scope) and not in viewModelScope:
 * when the user leaves the screen, the ViewModel can be cleared at the same moment the
 * save starts, and viewModelScope would cancel the save half-way. [saveScope] lives as long
 * as the app process, so nobody can cancel it. We also do not need NonCancellable for this.
 * NonCancellable only protects a block that has already started and still ties the work to
 * a dying object. Saves are sent through one queue ([commands]) that a single coroutine
 * reads in order. That keeps them in the order they happened (stop, then back) and makes a
 * duplicate insert impossible. [onCleared] only closes the queue. The coroutine still
 * finishes everything that is already in it.
 *
 * @param now clock for updatedAt. Replaced in tests.
 */
class NoteEditorViewModel(
    private val repository: NoteRepository,
    saveScope: CoroutineScope,
    private val now: () -> Long = { System.currentTimeMillis() }
) : ViewModel() {

    /** One open editor page. [id] is null until a new note was inserted. */
    private class Session(
        var id: Long?,
        var savedTitle: String,
        var savedBody: String
    )

    private sealed interface Command {
        class Save(val session: Session, val title: String, val body: String) : Command
        class Delete(val session: Session) : Command
    }

    private val _hasSavedNote = MutableStateFlow(false)

    /** True when the open note exists in storage (the UI shows the delete button then). */
    val hasSavedNote: StateFlow<Boolean> = _hasSavedNote.asStateFlow()

    @Volatile
    private var session: Session? = null
    private var draftTitle = ""
    private var draftBody = ""

    private val commands = Channel<Command>(Channel.UNLIMITED)

    init {
        saveScope.launch {
            for (command in commands) {
                try {
                    handle(command)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // Keep the queue alive. The next save tries again (the session state is unchanged).
                    Log.e("NoteEditorViewModel", "Saving failed", e)
                }
            }
        }
    }

    /**
     * Starts an editing session: [note] to edit, or null for a new note.
     * Does nothing if a session is already open (for example after rotating the screen),
     * so the text the user typed is kept.
     */
    fun open(note: Note?) {
        if (session != null) return
        session = Session(
            id = note?.id,
            savedTitle = note?.title?.trim() ?: "",
            savedBody = note?.body?.trim() ?: ""
        )
        draftTitle = note?.title ?: ""
        draftBody = note?.body ?: ""
        _hasSavedNote.value = note != null
    }

    /** Called while typing. Only remembers the text, nothing is saved. */
    fun onTitleChange(value: String) {
        draftTitle = value
    }

    /** Called while typing. Only remembers the text, nothing is saved. */
    fun onBodyChange(value: String) {
        draftBody = value
    }

    /** Back arrow or system back: save, and end the session. */
    fun onBack() {
        val current = session ?: return
        session = null
        commands.trySend(Command.Save(current, draftTitle, draftBody))
    }

    /** App went to the background: save, and keep the session open. */
    fun onStop() {
        val current = session ?: return
        commands.trySend(Command.Save(current, draftTitle, draftBody))
    }

    /** The user chose "Delete note". Removes the note if it was saved, and ends the session. */
    fun deleteNote() {
        val current = session ?: return
        session = null
        commands.trySend(Command.Delete(current))
    }

    override fun onCleared() {
        // Do not cancel anything. Close the queue: what is already in it is still saved.
        commands.close()
    }

    // ---- Runs one command at a time, in the order they were sent ----

    private suspend fun handle(command: Command) {
        when (command) {
            is Command.Save -> persist(command.session, command.title, command.body)
            is Command.Delete -> {
                val id = command.session.id
                if (id != null) repository.delete(id)
                command.session.id = null
            }
        }
    }

    private suspend fun persist(session: Session, rawTitle: String, rawBody: String) {
        val title = rawTitle.trim()
        val body = rawBody.trim()
        val id = session.id

        // Nothing written: a blank note is not worth saving ...
        if (title.isBlank() && body.isBlank()) {
            if (id != null) {
                // ... and an existing note that was emptied is deleted.
                repository.delete(id)
                session.id = null
                session.savedTitle = ""
                session.savedBody = ""
                publishHasSavedNote(session, false)
            }
            return
        }

        if (id == null) {
            // First save of a new note. Keep the id so the next save is an update.
            val newId = repository.insert(Note(id = 0L, title = title, body = body, updatedAt = now()))
            session.id = newId
            session.savedTitle = title
            session.savedBody = body
            publishHasSavedNote(session, true)
            return
        }

        if (title == session.savedTitle && body == session.savedBody) return // nothing changed

        repository.update(Note(id = id, title = title, body = body, updatedAt = now()))
        session.savedTitle = title
        session.savedBody = body
    }

    /** Only the page that is open now may change the flag (an older, closed page must not). */
    private fun publishHasSavedNote(from: Session, value: Boolean) {
        if (session === from) _hasSavedNote.value = value
    }

    companion object {
        /** Creates the ViewModel with the real storage (Room) and the application-level scope. */
        fun factory(context: Context): ViewModelProvider.Factory {
            val appContext = context.applicationContext
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return NoteEditorViewModel(
                        repository = RoomNoteRepository.get(appContext),
                        saveScope = ApplicationScope.scope
                    ) as T
                }
            }
        }
    }
}
