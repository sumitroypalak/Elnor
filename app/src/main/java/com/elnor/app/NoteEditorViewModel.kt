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
 *  - An existing note whose title and body were both cleared is moved to the Trash (it keeps
 *    the text that was last saved, so it can still be restored from there).
 *  - Deleting a note moves it to the Trash. Nothing is removed for good here. Only the Trash
 *    screen can do that.
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

    private val _lastSavedAt = MutableStateFlow<Long?>(null)

    /**
     * The time of the last save of the open note (the updatedAt it got), or null if it was never
     * saved. The editor shows it as "Last edited". It changes at every save, also for a note that
     * was new when the page was opened.
     */
    val lastSavedAt: StateFlow<Long?> = _lastSavedAt.asStateFlow()

    @Volatile
    private var session: Session? = null
    private var draftTitle = ""
    private var draftBody = ""
    private var draftSelStart = 0
    private var draftSelEnd = 0

    // Undo / Redo. It lives here (not in the screen), so it survives rotating the phone.
    // It is only used from the main thread.
    private var history = EditHistory(EditSnapshot("", ""), now)
    private val _canUndo = MutableStateFlow(false)
    private val _canRedo = MutableStateFlow(false)

    /** True when there is a change to take back (the Undo button is on). */
    val canUndo: StateFlow<Boolean> = _canUndo.asStateFlow()

    /** True when an undone change can be brought back (the Redo button is on). */
    val canRedo: StateFlow<Boolean> = _canRedo.asStateFlow()

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
        draftSelStart = draftBody.length
        draftSelEnd = draftBody.length
        history = EditHistory(snapshot(), now) // a new page starts with an empty Undo memory
        refreshHistoryFlags()
        _hasSavedNote.value = note != null
        _lastSavedAt.value = note?.updatedAt
    }

    /** Called while typing. Only remembers the text (and for Undo), nothing is saved. */
    fun onTitleChange(value: String) {
        draftTitle = value
        recordForUndo()
    }

    /**
     * Called while typing. Only remembers the text (and for Undo), nothing is saved.
     * [selStart] and [selEnd] are the cursor (or selection) in the text.
     */
    fun onBodyChange(value: String, selStart: Int = value.length, selEnd: Int = selStart) {
        draftBody = value
        draftSelStart = selStart.coerceIn(0, value.length)
        draftSelEnd = selEnd.coerceIn(0, value.length)
        recordForUndo()
    }

    /**
     * Takes back the last change. Returns what the page must show now (title, text and cursor),
     * or null if there is nothing to take back. The remembered text is changed too, so what is
     * saved is what is on the screen.
     */
    fun undo(): EditSnapshot? = applyFromHistory(if (session == null) null else history.undo())

    /** Brings back the change that was taken back. Same result as [undo]. */
    fun redo(): EditSnapshot? = applyFromHistory(if (session == null) null else history.redo())

    private fun snapshot() = EditSnapshot(draftTitle, draftBody, draftSelStart, draftSelEnd)

    private fun recordForUndo() {
        if (session == null) return
        history.record(snapshot())
        refreshHistoryFlags()
    }

    private fun applyFromHistory(result: EditSnapshot?): EditSnapshot? {
        if (result == null) return null
        draftTitle = result.title
        draftBody = result.body
        draftSelStart = result.bodySelStart
        draftSelEnd = result.bodySelEnd
        refreshHistoryFlags()
        return result
    }

    private fun refreshHistoryFlags() {
        _canUndo.value = history.canUndo
        _canRedo.value = history.canRedo
    }

    /** The page is closing: forget the changes, so the next page never starts with these buttons on. */
    private fun clearHistory() {
        history = EditHistory(EditSnapshot("", ""), now)
        refreshHistoryFlags()
    }

    /** Back arrow or system back: save, and end the session. */
    fun onBack() {
        val current = session ?: return
        session = null
        _lastSavedAt.value = null // the page is closing: the next page must not start with this time
        clearHistory()
        commands.trySend(Command.Save(current, draftTitle, draftBody))
    }

    /** App went to the background: save, and keep the session open. */
    fun onStop() {
        val current = session ?: return
        commands.trySend(Command.Save(current, draftTitle, draftBody))
    }

    /** The user chose "Delete note". Moves the note to the Trash if it was saved, and ends the session. */
    fun deleteNote() {
        val current = session ?: return
        session = null
        _lastSavedAt.value = null
        clearHistory()
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
                if (id != null) repository.moveToTrash(id)
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
                // ... and an existing note that was emptied leaves the list. It goes to the
                // Trash with the text that was last saved, so nothing is lost for good.
                repository.moveToTrash(id)
                session.id = null
                session.savedTitle = ""
                session.savedBody = ""
                publishHasSavedNote(session, false)
                publishLastSavedAt(session, null)
            }
            return
        }

        if (id == null) {
            // First save of a new note. Keep the id so the next save is an update.
            val savedAt = now()
            val newId = repository.insert(Note(id = 0L, title = title, body = body, updatedAt = savedAt))
            session.id = newId
            session.savedTitle = title
            session.savedBody = body
            publishHasSavedNote(session, true)
            publishLastSavedAt(session, savedAt)
            return
        }

        if (title == session.savedTitle && body == session.savedBody) return // nothing changed

        val savedAt = now()
        repository.update(Note(id = id, title = title, body = body, updatedAt = savedAt))
        session.savedTitle = title
        session.savedBody = body
        publishLastSavedAt(session, savedAt)
    }

    /** Only the page that is open now may change the flag (an older, closed page must not). */
    private fun publishHasSavedNote(from: Session, value: Boolean) {
        if (session === from) _hasSavedNote.value = value
    }

    /** Same rule as [publishHasSavedNote]: only the page that is open now may change the time. */
    private fun publishLastSavedAt(from: Session, value: Long?) {
        if (session === from) _lastSavedAt.value = value
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
