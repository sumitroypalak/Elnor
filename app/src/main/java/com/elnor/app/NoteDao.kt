package com.elnor.app

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * The order of the home list and of search: pinned notes first (the most recently pinned on top),
 * then the others, the most recently changed first.
 */
private const val LIST_ORDER = "pinnedAt IS NULL, pinnedAt DESC, updatedAt DESC"

/**
 * A note is either a normal note (deletedAt IS NULL) or a note in the Trash (deletedAt is the
 * time it was deleted). A normal note can also be archived (archivedAt is the time it was
 * archived). The home list only returns normal notes that are not archived. Search also finds
 * archived notes (the home screen marks them), and no query returns a note in the Trash.
 */
@Dao
interface NoteDao {

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND archivedAt IS NULL ORDER BY $LIST_ORDER")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND archivedAt IS NULL ORDER BY $LIST_ORDER")
    suspend fun getAll(): List<NoteEntity>

    /** Archived notes that are not in the Trash, the most recently archived first. */
    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND archivedAt IS NOT NULL ORDER BY archivedAt DESC")
    fun observeArchived(): Flow<List<NoteEntity>>

    /** Notes in the Trash, the most recently deleted first. */
    @Query("SELECT * FROM notes WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeTrashed(): Flow<List<NoteEntity>>

    /**
     * [escapedQuery] must come from SearchText.toLikeTerm(): normalized, lower case, and with
     * % _ \ escaped with a backslash. Never pass raw user text here.
     */
    @Query(
        "SELECT * FROM notes WHERE deletedAt IS NULL " +
            "AND searchText LIKE '%' || :escapedQuery || '%' ESCAPE '\\' " +
            "ORDER BY " + LIST_ORDER
    )
    fun search(escapedQuery: String): Flow<List<NoteEntity>>

    /** Returns the new row id. */
    @Insert
    suspend fun insert(note: NoteEntity): Long

    /**
     * Changes only the text of a note. deletedAt is deliberately not touched, so saving can
     * never take a note out of the Trash by accident.
     * Returns how many rows changed (0 if the note does not exist any more).
     */
    @Query(
        "UPDATE notes SET title = :title, body = :body, updatedAt = :updatedAt, " +
            "searchText = :searchText WHERE id = :id"
    )
    suspend fun updateContent(
        id: Long,
        title: String,
        body: String,
        updatedAt: Long,
        searchText: String
    ): Int

    /**
     * Puts a normal note in the Trash. A note that is already there keeps its first deletedAt.
     * The note is unpinned, so a restored note comes back as a normal note.
     */
    @Query("UPDATE notes SET deletedAt = :deletedAt, pinnedAt = NULL WHERE id = :id AND deletedAt IS NULL")
    suspend fun moveToTrash(id: Long, deletedAt: Long)

    /** Pins a normal note. A note that is already pinned keeps its place (its first pinnedAt). */
    @Query("UPDATE notes SET pinnedAt = :pinnedAt WHERE id = :id AND deletedAt IS NULL AND pinnedAt IS NULL")
    suspend fun pin(id: Long, pinnedAt: Long)

    /** Takes the pin off. Title, body and updatedAt are not touched. */
    @Query("UPDATE notes SET pinnedAt = NULL WHERE id = :id")
    suspend fun unpin(id: Long)

    /** Takes a note out of the Trash. Title, body and updatedAt were never changed. */
    @Query("UPDATE notes SET deletedAt = NULL WHERE id = :id")
    suspend fun restore(id: Long)

    // ---- Several notes at once (the selection on the home screen, the Archive screen) ----
    // The caller keeps each list short (see RoomNoteRepository), SQLite allows few variables.

    /** Same as [moveToTrash] for many notes. */
    @Query("UPDATE notes SET deletedAt = :deletedAt, pinnedAt = NULL WHERE id IN (:ids) AND deletedAt IS NULL")
    suspend fun moveToTrashMany(ids: List<Long>, deletedAt: Long)

    /** Pins normal notes that are not pinned yet. Pinned ones keep their place. */
    @Query(
        "UPDATE notes SET pinnedAt = :pinnedAt WHERE id IN (:ids) " +
            "AND deletedAt IS NULL AND archivedAt IS NULL AND pinnedAt IS NULL"
    )
    suspend fun pinMany(ids: List<Long>, pinnedAt: Long)

    @Query("UPDATE notes SET pinnedAt = NULL WHERE id IN (:ids)")
    suspend fun unpinMany(ids: List<Long>)

    /** Archives normal notes. Title, body, updatedAt and the pin are not touched. */
    @Query(
        "UPDATE notes SET archivedAt = :archivedAt WHERE id IN (:ids) " +
            "AND deletedAt IS NULL AND archivedAt IS NULL"
    )
    suspend fun archiveMany(ids: List<Long>, archivedAt: Long)

    @Query("UPDATE notes SET archivedAt = NULL WHERE id IN (:ids)")
    suspend fun unarchiveMany(ids: List<Long>)

    /** Removes the row for good. Only the Trash screen uses this. */
    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Removes every note that is in the Trash, for good. */
    @Query("DELETE FROM notes WHERE deletedAt IS NOT NULL")
    suspend fun deleteAllTrashed()

    /**
     * Removes the notes that were put in the Trash at [cutoff] or earlier, for good. A normal
     * note has deletedAt NULL, so it can never match, whatever its updatedAt is.
     * Returns how many rows were removed.
     */
    @Query("DELETE FROM notes WHERE deletedAt IS NOT NULL AND deletedAt <= :cutoff")
    suspend fun deleteTrashedUpTo(cutoff: Long): Int

    /** Every note, normal and in the Trash. Only the backup uses this. */
    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    suspend fun getEverything(): List<NoteEntity>

    /**
     * Inserts all rows in one transaction: either every note is added or none. Only the backup
     * import uses this. The ids must be 0 so Room gives new ones and nothing is overwritten.
     */
    @Insert
    suspend fun insertAll(notes: List<NoteEntity>)
}
