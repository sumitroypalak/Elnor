package com.elnor.app

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Which tasks the Task tab shows. Everything here works on plain values and has no Android code,
 * so it is tested on the computer.
 */
enum class TaskFilter(val label: String) {
    ALL("All"),
    ACTIVE("Active"),
    COMPLETED("Completed")
}

/**
 * How near the deadline of an open task is. A finished task has no urgency: it is [NONE] even if
 * its day has passed.
 */
enum class TaskUrgency { NONE, OVERDUE, TODAY, SOON, LATER }

/** Tasks that are not finished yet. */
fun activeTasks(tasks: List<Task>): List<Task> = tasks.filter { !it.isCompleted }

/** [tasks] that belong to [filter]. The order is not changed, see [sortTasks]. */
fun filterTasks(tasks: List<Task>, filter: TaskFilter): List<Task> = when (filter) {
    TaskFilter.ALL -> tasks
    TaskFilter.ACTIVE -> tasks.filter { !it.isCompleted }
    TaskFilter.COMPLETED -> tasks.filter { it.isCompleted }
}

/**
 * The order of the list: open tasks first, finished tasks after them.
 *  - Open tasks: the nearest deadline first, tasks without a deadline last. Tasks with the same
 *    deadline (or none) are ordered newest first.
 *  - Finished tasks: the most recently finished first.
 * Equal tasks are ordered by id, so the list never jumps around.
 */
fun sortTasks(tasks: List<Task>): List<Task> {
    val open = tasks.filter { !it.isCompleted }.sortedWith(
        compareBy<Task> { it.dueDate == null }
            .thenBy { it.dueDate ?: 0L }
            .thenByDescending { it.createdAt }
            .thenBy { it.id }
    )
    val done = tasks.filter { it.isCompleted }.sortedWith(
        compareByDescending<Task> { it.completedAt ?: 0L }
            .thenBy { it.id }
    )
    return open + done
}

/** Days from [today] to the deadline: negative when the deadline has passed. */
fun daysUntil(dueDate: Long, today: LocalDate): Long = dueDate - today.toEpochDay()

fun taskUrgency(task: Task, today: LocalDate): TaskUrgency {
    val due = task.dueDate ?: return TaskUrgency.NONE
    if (task.isCompleted) return TaskUrgency.NONE
    val days = daysUntil(due, today)
    return when {
        days < 0 -> TaskUrgency.OVERDUE
        days == 0L -> TaskUrgency.TODAY
        days <= 2L -> TaskUrgency.SOON
        else -> TaskUrgency.LATER
    }
}

private val SHORT_DATE = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)
private val SHORT_DATE_WITH_YEAR = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

/**
 * The deadline as shown under the task title: "Today", "Tomorrow", "Yesterday", "Fri, 30 Oct", or
 * "30 Oct 2027" when the year is not the current one.
 */
fun formatDueDate(dueDate: Long, today: LocalDate): String {
    val date = LocalDate.ofEpochDay(dueDate)
    return when (daysUntil(dueDate, today)) {
        0L -> "Today"
        1L -> "Tomorrow"
        -1L -> "Yesterday"
        else -> if (date.year == today.year) {
            date.format(SHORT_DATE)
        } else {
            date.format(SHORT_DATE_WITH_YEAR)
        }
    }
}

/** [formatDueDate], with "Overdue · " in front when an open task has missed its deadline. */
fun deadlineLabel(task: Task, today: LocalDate): String? {
    val due = task.dueDate ?: return null
    val text = formatDueDate(due, today)
    return if (taskUrgency(task, today) == TaskUrgency.OVERDUE) "Overdue · $text" else text
}

/** "3 tasks left", "1 task left", "All done" (no tasks left) for the line above the list. */
fun taskCountLabel(tasks: List<Task>): String {
    val left = activeTasks(tasks).size
    return when {
        tasks.isEmpty() -> "No tasks yet"
        left == 0 -> "All done"
        left == 1 -> "1 task left"
        else -> "$left tasks left"
    }
}
