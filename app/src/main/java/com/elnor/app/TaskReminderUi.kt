@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.elnor.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.text.format.DateFormat
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import java.time.LocalDate
import java.time.ZoneId

private const val MILLIS_PER_DAY = 86_400_000L

/**
 * The "Reminder" part of the task dialog: a button that opens the date picker and then the time
 * picker, the chosen time with a button to remove it, and the question for the notification
 * permission (Android 13 and newer) the first time a reminder is set.
 *
 * [remindAt] is the moment of the reminder (see [TaskEntity.remindAt]) or null; [dueDate] is the
 * deadline of the task, offered as the first day of the date picker.
 */
@Composable
fun ReminderSection(
    remindAt: Long?,
    dueDate: Long?,
    today: LocalDate,
    onChange: (Long?) -> Unit
) {
    val context = LocalContext.current
    val zone = ZoneId.systemDefault()
    val is24Hour = DateFormat.is24HourFormat(context)

    // 0 = nothing open, 1 = date picker, 2 = time picker for [pickedDay]
    var stage by rememberSaveable { mutableStateOf(0) }
    var pickedDay by rememberSaveable { mutableStateOf(today.toEpochDay()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* if it is refused the reminder is stored, but Android shows no notification */ }

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Reminder",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = remindAt != null,
                onClick = {
                    pickedDay = remindAt?.let { ReminderTime.dayOf(it, zone) }
                        ?: dueDate?.takeIf { it >= today.toEpochDay() }
                        ?: today.toEpochDay()
                    stage = 1
                },
                leadingIcon = {
                    Icon(Icons.Default.Notifications, contentDescription = null, modifier = Modifier.size(18.dp))
                },
                label = {
                    Text(
                        if (remindAt != null) {
                            ReminderTime.label(remindAt, System.currentTimeMillis(), zone, is24Hour)
                        } else "Set reminder"
                    )
                }
            )
        }
        if (remindAt != null) {
            TextButton(onClick = { onChange(null) }) {
                Icon(Icons.Default.Clear, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Remove reminder")
            }
        }
    }

    if (stage == 1) {
        // Same trick as the deadline: the date picker works with the UTC midnight of the day.
        val dateState = rememberDatePickerState(initialSelectedDateMillis = pickedDay * MILLIS_PER_DAY)
        DatePickerDialog(
            onDismissRequest = { stage = 0 },
            confirmButton = {
                TextButton(onClick = {
                    dateState.selectedDateMillis?.let { pickedDay = Math.floorDiv(it, MILLIS_PER_DAY) }
                    stage = 2
                }) { Text("Next") }
            },
            dismissButton = { TextButton(onClick = { stage = 0 }) { Text("Cancel") } }
        ) {
            DatePicker(state = dateState)
        }
    }

    if (stage == 2) {
        val now = System.currentTimeMillis()
        // Start at the time of the old reminder, else 9:00 (or the next full hour when 9:00 of
        // today has already passed)
        val startMillis = remindAt?.takeIf { ReminderTime.dayOf(it, zone) == pickedDay }
            ?: ReminderTime.toMillis(pickedDay, ReminderTime.DEFAULT_HOUR, 0, zone).let { nine ->
                if (nine > now) nine else now + 3_600_000L
            }
        val timeState = rememberTimePickerState(
            initialHour = ReminderTime.hourOf(startMillis, zone),
            initialMinute = if (startMillis == now + 3_600_000L) 0 else ReminderTime.minuteOf(startMillis, zone),
            is24Hour = is24Hour
        )
        AlertDialog(
            onDismissRequest = { stage = 0 },
            title = { Text(formatDueDate(pickedDay, today)) },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    TimePicker(state = timeState)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val chosen = ReminderTime.toMillis(pickedDay, timeState.hour, timeState.minute, zone)
                    if (chosen <= System.currentTimeMillis()) {
                        Toast.makeText(context, "That time has already passed", Toast.LENGTH_SHORT).show()
                    } else {
                        onChange(chosen)
                        stage = 0
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
                            PackageManager.PERMISSION_GRANTED
                        ) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { stage = 1 }) { Text("Back") } }
        )
    }
}
