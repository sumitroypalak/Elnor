package com.elnor.app

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * Room (SQLite) storage. [scope] keeps [notes] up to date; tests pass their own scope.
 * [now] is the clock for the time a note was deleted. Replaced in tests.
 */
class RoomNoteRepository(
    private val dao: NoteDao,
    scope: CoroutineScope,
    private val now: () -> Long = { System.currentTimeMillis() }
) : NoteRepository {

    private val _notesLoaded = MutableStateFlow(false)

    // The list starts empty and fills as soon as the database answers (on a background thread).
    // Nothing blocks the main thread at start-up. [notesLoaded] turns true with the first real
    // value, so the screen can show a loading state and "Your space is empty" never flashes.
    override val notes: StateFlow<List<Note>> = dao.observeAll()
        .map { list -> list.map { it.toNote() } }
        .onEach { _notesLoaded.value = true }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override val notesLoaded: StateFlow<Boolean> = _notesLoaded.asStateFlow()

    override val trashedNotes: Flow<List<Note>> = dao.observeTrashed()
        .map { list -> list.map { it.toNote() } }

    override fun search(query: String): Flow<List<Note>> {
        if (query.isBlank()) return notes
        return dao.search(SearchText.toLikeTerm(query))
            .map { list -> list.map { it.toNote() } }
    }

    // id = 0 means "new row": Room gives the next free id and returns it.
    // A new note is never in the Trash, whatever deletedAt the given note has.
    override suspend fun insert(note: Note): Long =
        dao.insert(note.toEntity().copy(id = 0L, deletedAt = null))

    // toEntity() builds searchText again from the new title and body.
    // Only the text columns are written, so deletedAt is never changed by a save.
    override suspend fun update(note: Note) {
        val entity = note.toEntity()
        dao.updateContent(
            id = entity.id,
            title = entity.title,
            body = entity.body,
            updatedAt = entity.updatedAt,
            searchText = entity.searchText
        )
    }

    // The id inside [note] is used as it is (a new note chooses its id when its page opens), so a
    // page that comes back after the process was closed can insert under the same id. A taken id
    // makes the insert fail (it never replaces a note).
    override suspend fun insertWithId(note: Note): Long {
        if (note.id <= 0L) return insert(note)
        return dao.insert(note.toEntity().copy(deletedAt = null))
    }

    override suspend fun updateIfExists(note: Note): Boolean {
        val entity = note.toEntity()
        return dao.updateContent(
            id = entity.id,
            title = entity.title,
            body = entity.body,
            updatedAt = entity.updatedAt,
            searchText = entity.searchText
        ) > 0
    }

    override suspend fun moveToTrash(id: Long) {
        dao.moveToTrash(id, now())
    }

    override suspend fun setPinned(id: Long, pinned: Boolean) {
        if (pinned) dao.pin(id, now()) else dao.unpin(id)
    }

    override suspend fun restore(id: Long) {
        dao.restore(id)
    }

    override suspend fun deletePermanently(id: Long) {
        dao.deleteById(id)
    }

    override suspend fun emptyTrash() {
        dao.deleteAllTrashed()
    }

    // A note is expired when now - deletedAt >= 30 days, that is deletedAt <= now - 30 days.
    // If the phone's clock goes backwards, the cutoff moves back too, so nothing is removed early.
    override suspend fun purgeExpiredTrash(): Int =
        dao.deleteTrashedUpTo(now() - TRASH_RETENTION_MS)

    override suspend fun exportAll(): List<Note> =
        dao.getEverything().map { it.toNote() }

    // toEntity() builds searchText for every imported note, so search finds them at once.
    override suspend fun importNotes(notes: List<Note>): ImportSummary {
        val existing = dao.getEverything().map { it.toNote() }
        val fresh = BackupFormat.selectNew(existing, notes)
        if (fresh.isNotEmpty()) {
            dao.insertAll(fresh.map { it.toEntity().copy(id = 0L) })
        }
        return ImportSummary(added = fresh.size, alreadyPresent = notes.size - fresh.size)
    }

    companion object {
        @Volatile
        private var instance: RoomNoteRepository? = null

        fun get(context: Context): RoomNoteRepository {
            return instance ?: synchronized(this) {
                instance ?: create(context.applicationContext).also { instance = it }
            }
        }

        private fun create(appContext: Context): RoomNoteRepository {
            val database = Room.databaseBuilder(appContext, AppDatabase::class.java, "elnor_notes_room.db")
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .addCallback(LegacyNotesImport(appContext))
                .build()
            return RoomNoteRepository(database.noteDao(), ApplicationScope.scope)
        }
    }
}
