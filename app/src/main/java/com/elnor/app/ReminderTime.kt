package com.elnor.app

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The time of a task reminder. It is one point in time ([Task.remindAt], milliseconds since 1970),
 * not a day and a clock time, so an alarm for it goes off at the same moment wherever the phone is.
 * The day and the clock time the user picked are turned into that moment with the time zone of the
 * phone when the reminder is set ([toMillis]), and turned back for showing ([dayOf], [hourOf],
 * [minuteOf], [label]).
 *
 * Everything here works on plain values and has no Android code, so it is tested on the computer.
 */
object ReminderTime {

    /** The clock hour the time picker starts at when nothing was chosen yet. */
    const val DEFAULT_HOUR = 9

    private val TIME_12_HOUR = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    private val TIME_24_HOUR = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

    /**
     * The moment of [hour]:[minute] on the day [epochDay] (a day number, see [Task.dueDate]) in
     * [zone]. A clock time that does not exist on that day (the hour skipped when the clocks go
     * forward) is moved forward by the length of the gap, so 02:30 becomes 03:30.
     */
    fun toMillis(epochDay: Long, hour: Int, minute: Int, zone: ZoneId): Long =
        LocalDateTime.of(LocalDate.ofEpochDay(epochDay), LocalTime.of(hour, minute))
            .atZone(zone)
            .toInstant()
            .toEpochMilli()

    private fun at(millis: Long, zone: ZoneId): ZonedDateTime =
        Instant.ofEpochMilli(millis).atZone(zone)

    /** The day number ([LocalDate.toEpochDay]) of [millis] in [zone]. */
    fun dayOf(millis: Long, zone: ZoneId): Long = at(millis, zone).toLocalDate().toEpochDay()

    fun hourOf(millis: Long, zone: ZoneId): Int = at(millis, zone).hour

    fun minuteOf(millis: Long, zone: ZoneId): Int = at(millis, zone).minute

    /**
     * The reminder as shown under the task title: the day like the deadline ("Today", "Tomorrow",
     * "Fri, 20 Nov") and the clock time, for example "Tomorrow, 9:00 AM" or, with [is24Hour],
     * "Tomorrow, 09:00". "Today" is the day of [now] in [zone].
     */
    fun label(remindAt: Long, now: Long, zone: ZoneId, is24Hour: Boolean): String {
        val today = at(now, zone).toLocalDate()
        val time = at(remindAt, zone).format(if (is24Hour) TIME_24_HOUR else TIME_12_HOUR)
        return formatDueDate(dayOf(remindAt, zone), today) + ", " + time
    }
}

/**
 * Should an alarm be set for a task? Only for a task that is not finished and whose reminder is
 * still to come. Everything else needs no alarm (and an old one is taken away).
 */
fun shouldScheduleReminder(remindAt: Long, completed: Boolean, now: Long): Boolean =
    !completed && remindAt > now
