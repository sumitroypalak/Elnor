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
     * [dueDate] is a day number, see [TaskEntity.dueDate]. [remindAt] is the moment of the
     * reminder, see [TaskEntity.remindAt].
     */
    suspend fun add(title: String, dueDate: Long?, remindAt: Long? = null): Long?

    /** Changes the title, the deadline and the reminder. A blank [title] is ignored. */
    suspend fun update(id: Long, title: String, dueDate: Long?, remindAt: Long? = null)

    /** Checks the task off ([completed] true) or opens it again. */
    suspend fun setCompleted(id: Long, completed: Boolean)

    suspend fun delete(id: Long)

    /** Puts the task in a collection, or takes it out ([collectionId] null). */
    suspend fun setCollection(id: Long, collectionId: Long?)

    /** Deletes every finished task. Returns how many were deleted. */
    suspend fun clearCompleted(): Int
}

/**
 * Room (SQLite) storage for tasks. [now] is the clock for the time of creation and completion.
 * Replaced in tests. [reminders] sets and takes away the alarms: it is told whenever a task is
 * added, changed, checked off, opened again or deleted, so the alarms always follow the tasks.
 */
class RoomTaskRepository(
    private val dao: TaskDao,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val reminders: ReminderScheduler = ReminderScheduler.None
) : TaskRepository {

    override val tasks: Flow<List<Task>> = dao.observeAll()
        .map { list -> list.map { it.toTask() } }

    override suspend fun add(title: String, dueDate: Long?, remindAt: Long?): Long? {
        val clean = title.trim()
        if (clean.isEmpty()) return null
        val id = dao.insert(
            TaskEntity(title = clean, createdAt = now(), dueDate = dueDate, remindAt = remindAt)
        )
        syncAlarm(id)
        return id
    }

    override suspend fun update(id: Long, title: String, dueDate: Long?, remindAt: Long?) {
        val clean = title.trim()
        if (clean.isEmpty()) return
        dao.updateDetails(id, clean, dueDate, remindAt)
        syncAlarm(id)
    }

    override suspend fun setCompleted(id: Long, completed: Boolean) {
        dao.setCompletedAt(id, if (completed) now() else null)
        syncAlarm(id)
    }

    override suspend fun delete(id: Long) {
        dao.deleteById(id)
        reminders.cancel(id)
    }

    override suspend fun setCollection(id: Long, collectionId: Long?) {
        dao.setCollection(id, collectionId)
    }

    override suspend fun clearCompleted(): Int {
        // a finished task has no alarm any more (checking it off took it away), so nothing to cancel
        return dao.deleteCompleted()
    }

    /** Makes the alarm of the task [id] match what is stored: set when it is due, else removed. */
    private suspend fun syncAlarm(id: Long) {
        val task = dao.getById(id)
        val at = task?.remindAt
        if (task != null && at != null && shouldScheduleReminder(at, task.completedAt != null, now())) {
            reminders.schedule(id, task.title, at)
        } else {
            reminders.cancel(id)
        }
    }

    companion object {
        @Volatile
        private var instance: RoomTaskRepository? = null

        fun get(context: Context): RoomTaskRepository {
            return instance ?: synchronized(this) {
                instance ?: RoomTaskRepository(
                    dao = ElnorDatabase.get(context.applicationContext).taskDao(),
                    reminders = AlarmReminderScheduler(context.applicationContext)
                ).also { instance = it }
            }
        }
    }
}
