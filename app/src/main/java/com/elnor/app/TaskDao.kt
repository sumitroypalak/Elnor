package com.elnor.app

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/**
 * Storage queries for the Task tab. The order of the list is decided in Kotlin
 * (see [sortTasks]), so this file only reads and writes rows.
 */
@Dao
interface TaskDao {

    /** Every task, open and finished. Updates by itself when a task changes. */
    @Query("SELECT * FROM tasks")
    fun observeAll(): Flow<List<TaskEntity>>

    /** Returns the new row id. */
    @Insert
    suspend fun insert(task: TaskEntity): Long

    /** Changes the title and the deadline. The completion state is not touched. */
    @Query("UPDATE tasks SET title = :title, dueDate = :dueDate WHERE id = :id")
    suspend fun updateDetails(id: Long, title: String, dueDate: Long?)

    /** [completedAt] is the time it was checked off, or null to open the task again. */
    @Query("UPDATE tasks SET completedAt = :completedAt WHERE id = :id")
    suspend fun setCompletedAt(id: Long, completedAt: Long?)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: Long)

    /** Removes every finished task. Returns how many rows were removed. */
    @Query("DELETE FROM tasks WHERE completedAt IS NOT NULL")
    suspend fun deleteCompleted(): Int
}
