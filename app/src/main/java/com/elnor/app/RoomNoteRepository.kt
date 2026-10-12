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

/** Room (SQLite) storage. [scope] keeps [notes] up to date; tests pass their own scope. */
class RoomNoteRepository(
    private val dao: NoteDao,
    scope: CoroutineScope
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

    override fun search(query: String): Flow<List<Note>> {
        if (query.isBlank()) return notes
        return dao.search(SearchText.toLikeTerm(query))
            .map { list -> list.map { it.toNote() } }
    }

    // id = 0 means "new row": Room gives the next free id and returns it.
    override suspend fun insert(note: Note): Long = dao.insert(note.toEntity().copy(id = 0L))

    // toEntity() builds searchText again from the new title and body.
    override suspend fun update(note: Note) {
        dao.update(note.toEntity())
    }

    override suspend fun delete(id: Long) {
        dao.deleteById(id)
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
                .addMigrations(MIGRATION_1_2)
                .addCallback(LegacyNotesImport(appContext))
                .build()
            return RoomNoteRepository(database.noteDao(), ApplicationScope.scope)
        }
    }
}
