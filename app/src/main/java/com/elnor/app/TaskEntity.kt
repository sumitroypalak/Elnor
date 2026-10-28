package com.elnor.app

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * One to-do of the Task tab.
 *
 * - [title] is saved as the user typed it, without spaces at the start and the end.
 * - [dueDate] is the deadline as a day number (java.time.LocalDate.toEpochDay), or null when the
 *   task has no deadline. It is a whole day, not a time of day, and it does not depend on the
 *   time zone: a task due on the 30th stays due on the 30th when the phone travels.
 * - [completedAt] is null for an open task. A finished task has the time it was checked off.
 *   Unchecking it sets it back to null, and nothing else of the task changes.
 */
@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val createdAt: Long,
    val dueDate: Long? = null,
    val completedAt: Long? = null
)

/** The task as the screens see it. */
data class Task(
    val id: Long,
    val title: String,
    val createdAt: Long,
    val dueDate: Long? = null,
    val completedAt: Long? = null
) {
    val isCompleted: Boolean get() = completedAt != null
}

fun TaskEntity.toTask(): Task = Task(
    id = id,
    title = title,
    createdAt = createdAt,
    dueDate = dueDate,
    completedAt = completedAt
)
