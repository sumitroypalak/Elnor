package com.elnor.app

/**
 * The message under the Task tab right after a task is checked off ("Buy milk" completed). It comes
 * with an "Undo" button, so a task that was checked by mistake can be opened again.
 *
 * [title] is the task's title, already shortened by the caller when it is long. A blank title
 * gives a plain "Task completed". No Android classes here, so it is tested with plain unit tests.
 */
fun taskCompletedMessage(title: String): String {
    val clean = title.trim()
    return if (clean.isEmpty()) "Task completed" else "\"$clean\" completed"
}
