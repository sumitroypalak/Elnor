package com.elnor.app

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Where tasks are stored. The Task screen only knows this interface, so tests can use a fake.
 */
interface TaskRepository {
    /** Every task, open and finished, in no particular order (use [sortTasks]). Updates by itself. */
    val tasks: Flow<List<Task>>

    /**
     * Stores a new open task and returns its id, or null when [title] is blank (nothing is stored).
     * [dueDate] is a day number, see [TaskEntity.dueDate].
     */
    suspend fun add(title: String, dueDate: Long?): Long?

    /** Changes the title and the deadline. A blank [title] is ignored. */
    suspend fun update(id: Long, title: String, dueDate: Long?)

    /** Checks the task off ([completed] true) or opens it again. */
    suspend fun setCompleted(id: Long, completed: Boolean)

    suspend fun delete(id: Long)

    /** Deletes every finished task. Returns how many were deleted. */
    suspend fun clearCompleted(): Int
}

/**
 * Room (SQLite) storage for tasks. [now] is the clock for the time of creation and completion.
 * Replaced in tests.
 */
class RoomTaskRepository(
    private val dao: TaskDao,
    private val now: () -> Long = { System.currentTimeMillis() }
) : TaskRepository {

    override val tasks: Flow<List<Task>> = dao.observeAll()
        .map { list -> list.map { it.toTask() } }

    override suspend fun add(title: String, dueDate: Long?): Long? {
        val clean = title.trim()
        if (clean.isEmpty()) return null
        return dao.insert(TaskEntity(title = clean, createdAt = now(), dueDate = dueDate))
    }

    override suspend fun update(id: Long, title: String, dueDate: Long?) {
        val clean = title.trim()
        if (clean.isEmpty()) return
        dao.updateDetails(id, clean, dueDate)
    }

    override suspend fun setCompleted(id: Long, completed: Boolean) {
        dao.setCompletedAt(id, if (completed) now() else null)
    }

    override suspend fun delete(id: Long) {
        dao.deleteById(id)
    }

    override suspend fun clearCompleted(): Int = dao.deleteCompleted()

    companion object {
        @Volatile
        private var instance: RoomTaskRepository? = null

        fun get(context: Context): RoomTaskRepository {
            return instance ?: synchronized(this) {
                instance ?: RoomTaskRepository(ElnorDatabase.get(context.applicationContext).taskDao())
                    .also { instance = it }
            }
        }
    }
}
