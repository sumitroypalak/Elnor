package com.elnor.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

/**
 * The alarm of a task reminder went off: show the notification.
 *
 * The task is read again first. If it was deleted, finished or given another time since the alarm
 * was set, nothing is shown, so a reminder is never wrong. While the App lock is on the
 * notification does not show the title of the task, because the notification can be read on the
 * lock screen.
 */
class TaskReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val taskId = intent.getLongExtra(AlarmReminderScheduler.EXTRA_TASK_ID, -1L)
        if (taskId < 0) return
        val appContext = context.applicationContext
        val result = goAsync()
        ApplicationScope.scope.launch {
            try {
                val task = ElnorDatabase.get(appContext).taskDao().getById(taskId)
                val remindAt = task?.remindAt
                // A little early is fine (the system may fire an alarm slightly before); a
                // reminder that moved to a later time is not due yet.
                if (task != null && task.completedAt == null && remindAt != null &&
                    remindAt <= System.currentTimeMillis() + EARLY_TOLERANCE_MS
                ) {
                    show(appContext, task)
                }
            } finally {
                result.finish()
            }
        }
    }

    private fun show(context: Context, task: TaskEntity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) return

        ensureChannel(context)
        val locked = AppLock.isEnabledInStorage(context)
        val open = PendingIntent.getActivity(
            context,
            task.id.toInt(),
            Intent(context, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_reminder)
            .setContentTitle(
                if (locked) context.getString(R.string.reminder_locked_title) else task.title
            )
            .setContentText(
                if (locked) context.getString(R.string.reminder_locked_text)
                else context.getString(R.string.reminder_channel_name)
            )
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(
                if (locked) NotificationCompat.VISIBILITY_PRIVATE else NotificationCompat.VISIBILITY_PUBLIC
            )
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        NotificationManagerCompat.from(context).notify(task.id.toInt(), notification)
    }

    companion object {
        const val CHANNEL_ID = "task_reminders"
        private const val EARLY_TOLERANCE_MS = 60_000L

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.reminder_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply { description = context.getString(R.string.reminder_channel_description) }
            manager.createNotificationChannel(channel)
        }
    }
}
