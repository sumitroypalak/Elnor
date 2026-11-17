package com.elnor.app

/**
 * Sets and takes away the alarms of the task reminders. The task repository only knows this
 * interface, so it can be used without Android (tests use [None]) and the way the alarms are set
 * can be changed without touching the storage code. The real one is [AlarmReminderScheduler].
 *
 * An alarm belongs to the id of its task: setting an alarm for a task that already has one
 * replaces it, so there is never more than one alarm per task.
 */
interface ReminderScheduler {

    /** The reminder of the task [taskId] goes off at [atMillis] (milliseconds since 1970). */
    fun schedule(taskId: Long, title: String, atMillis: Long)

    /** Takes away the alarm of the task [taskId], if it has one. */
    fun cancel(taskId: Long)

    /** Does nothing. For tests, and for code that has no reminders. */
    object None : ReminderScheduler {
        override fun schedule(taskId: Long, title: String, atMillis: Long) = Unit

        override fun cancel(taskId: Long) = Unit
    }
}
