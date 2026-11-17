package com.elnor.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build

/**
 * Sets the alarms of the task reminders with the phone's [AlarmManager]. The alarm goes off even
 * when the phone sleeps (Doze) and is delivered to [TaskReminderReceiver].
 *
 * From Android 12 an app needs the user's permission to set an alarm at the exact minute. When it
 * is not given, the alarm is still set, but the system may move it a little (a few minutes at
 * most): a reminder that is a little late is better than none.
 */
class AlarmReminderScheduler(context: Context) : ReminderScheduler {

    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    override fun schedule(taskId: Long, title: String, atMillis: Long) {
        val pending = pendingIntent(appContext, taskId, title)
        try {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
            }
        } catch (_: SecurityException) {
            // The permission was taken away just now: fall back to the inexact alarm.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, pending)
        }
    }

    override fun cancel(taskId: Long) {
        alarmManager.cancel(pendingIntent(appContext, taskId, ""))
    }

    companion object {
        const val EXTRA_TASK_ID = "task_id"
        const val EXTRA_TITLE = "title"

        /**
         * One PendingIntent per task: the data (elnor://task/ID) makes the intents of different
         * tasks different, and the same task always gives the same one, so setting it again
         * replaces the old alarm and [cancel] finds it. The title is only extra data.
         */
        private fun pendingIntent(context: Context, taskId: Long, title: String): PendingIntent {
            val intent = Intent(context, TaskReminderReceiver::class.java)
                .setData(Uri.parse("elnor://task/$taskId"))
                .putExtra(EXTRA_TASK_ID, taskId)
                .putExtra(EXTRA_TITLE, title)
            return PendingIntent.getBroadcast(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }
}
