package com.elnor.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.launch

/**
 * Sets the alarms of all upcoming reminders again. Android forgets alarms when the phone restarts
 * and when the app is updated, and the alarms may need to change when the permission for exact
 * alarms is given or taken away.
 */
class ReminderRestoreReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            ACTION_EXACT_ALARM_STATE_CHANGED -> Unit
            else -> return
        }
        val appContext = context.applicationContext
        val result = goAsync()
        ApplicationScope.scope.launch {
            try {
                val scheduler = AlarmReminderScheduler(appContext)
                val now = System.currentTimeMillis()
                ElnorDatabase.get(appContext).taskDao().withUpcomingReminder(now).forEach { task ->
                    task.remindAt?.let { scheduler.schedule(task.id, task.title, it) }
                }
            } finally {
                result.finish()
            }
        }
    }

    private companion object {
        const val ACTION_EXACT_ALARM_STATE_CHANGED =
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED"
    }
}
