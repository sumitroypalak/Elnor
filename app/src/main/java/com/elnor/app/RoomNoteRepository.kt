package com.elnor.app

import android.content.Context
import androidx.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking

/**
 * Room (SQLite) storage. [scope] keeps [notes] up to date; tests pass their own scope.
 * [now] is the clock for the time a note was deleted. Replaced in tests.
 */
class RoomNoteRepository(
    private val dao: NoteDao,
    scope: CoroutineScope,
    private val now: () -> Long = { System.currentTimeMillis() }
) : NoteRepository {

    // The first value is read once, right away. Without it the list would be empty for a
    // moment when the app starts, and "Your space is empty" would flash on the screen.
    override val notes: StateFlow<List<Note>> = dao.observeAll()
        .map { list -> list.map { it.toNote() } }
        .stateIn(
            scope,
            SharingStarted.Eagerly,
            runBlocking(Dispatchers.IO) { dao.getAll() }.map { it.toNote() }
        )

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
