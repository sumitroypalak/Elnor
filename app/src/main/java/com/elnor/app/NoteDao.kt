package com.elnor.app

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface NoteDao {

    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes ORDER BY updatedAt DESC")
    suspend fun getAll(): List<NoteEntity>

    /**
     * [escapedQuery] must come from SearchText.toLikeTerm(): normalized, lower case, and with
     * % _ \ escaped with a backslash. Never pass raw user text here.
     */
    @Query(
        "SELECT * FROM notes WHERE searchText LIKE '%' || :escapedQuery || '%' ESCAPE '\\' " +
            "ORDER BY updatedAt DESC"
    )
    fun search(escapedQuery: String): Flow<List<NoteEntity>>

    /** Returns the new row id. */
    @Insert
    suspend fun insert(note: NoteEntity): Long

    /** Returns how many rows changed (0 if the note does not exist any more). */
    @Update
    suspend fun update(note: NoteEntity): Int

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: Long)
}
