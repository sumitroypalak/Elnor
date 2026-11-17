package com.elnor.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderTimeTest {

    private val dhaka = ZoneId.of("Asia/Dhaka") // UTC+6, no summer time
    private val newYork = ZoneId.of("America/New_York")

    private fun day(year: Int, month: Int, dayOfMonth: Int) = LocalDate.of(year, month, dayOfMonth).toEpochDay()

    private fun millis(zone: ZoneId, y: Int, mo: Int, d: Int, h: Int, mi: Int): Long =
        ZonedDateTime.of(y, mo, d, h, mi, 0, 0, zone).toInstant().toEpochMilli()

    // ---- day + clock time <-> one moment ----

    @Test
    fun toMillis_uses_the_time_zone_of_the_phone() {
        // 09:30 in Dhaka (UTC+6) is 03:30 UTC
        assertEquals(
            Instant.parse("2026-11-17T03:30:00Z").toEpochMilli(),
            ReminderTime.toMillis(day(2026, 11, 17), 9, 30, dhaka)
        )
        // the same clock time in New York (UTC-5 in November) is a different moment
        assertEquals(
            Instant.parse("2026-11-17T14:30:00Z").toEpochMilli(),
            ReminderTime.toMillis(day(2026, 11, 17), 9, 30, newYork)
        )
    }

    @Test
    fun day_hour_and_minute_come_back_out_of_the_moment() {
        val moment = ReminderTime.toMillis(day(2026, 11, 17), 18, 45, dhaka)
        assertEquals(day(2026, 11, 17), ReminderTime.dayOf(moment, dhaka))
        assertEquals(18, ReminderTime.hourOf(moment, dhaka))
        assertEquals(45, ReminderTime.minuteOf(moment, dhaka))
    }

    @Test
    fun the_same_moment_can_be_another_day_in_another_zone() {
        val moment = Instant.parse("2026-11-17T20:00:00Z").toEpochMilli()
        assertEquals(day(2026, 11, 18), ReminderTime.dayOf(moment, dhaka)) // 02:00 the next day
        assertEquals(day(2026, 11, 17), ReminderTime.dayOf(moment, newYork)) // 15:00
    }

    @Test
    fun midnight_belongs_to_the_new_day() {
        val moment = ReminderTime.toMillis(day(2026, 11, 17), 0, 0, dhaka)
        assertEquals(day(2026, 11, 17), ReminderTime.dayOf(moment, dhaka))
        assertEquals(0, ReminderTime.hourOf(moment, dhaka))
    }

    @Test
    fun a_clock_time_in_the_skipped_hour_moves_forward() {
        // New York turned its clocks forward on 8 March 2026: 02:00 became 03:00
        val moment = ReminderTime.toMillis(day(2026, 3, 8), 2, 30, newYork)
        assertEquals(3, ReminderTime.hourOf(moment, newYork))
        assertEquals(30, ReminderTime.minuteOf(moment, newYork))
        assertEquals(day(2026, 3, 8), ReminderTime.dayOf(moment, newYork))
    }

    // ---- the text under the task title ----

    // Monday 16 November 2026, 13:00 in Dhaka
    private val now = millis(dhaka, 2026, 11, 16, 13, 0)

    private fun label(y: Int, mo: Int, d: Int, h: Int, mi: Int, is24Hour: Boolean = false) =
        ReminderTime.label(millis(dhaka, y, mo, d, h, mi), now, dhaka, is24Hour)

    @Test
    fun label_says_today_and_tomorrow() {
        assertEquals("Today, 6:00 PM", label(2026, 11, 16, 18, 0))
        assertEquals("Tomorrow, 9:00 AM", label(2026, 11, 17, 9, 0))
    }

    @Test
    fun label_of_a_later_day_has_the_weekday_and_date() {
        assertEquals("Fri, 20 Nov, 9:05 AM", label(2026, 11, 20, 9, 5))
    }

    @Test
    fun label_of_another_year_has_the_year() {
        assertEquals("5 Jan 2027, 9:00 AM", label(2027, 1, 5, 9, 0))
    }

    @Test
    fun label_of_a_reminder_that_has_passed() {
        assertEquals("Yesterday, 9:00 AM", label(2026, 11, 15, 9, 0))
        assertEquals("Today, 8:00 AM", label(2026, 11, 16, 8, 0))
    }

    @Test
    fun label_has_a_12_hour_and_a_24_hour_form() {
        assertEquals("Today, 12:00 AM", label(2026, 11, 16, 0, 0))
        assertEquals("Today, 12:00 PM", label(2026, 11, 16, 12, 0))
        assertEquals("Today, 18:00", label(2026, 11, 16, 18, 0, is24Hour = true))
        assertEquals("Today, 00:05", label(2026, 11, 16, 0, 5, is24Hour = true))
    }

    @Test
    fun today_follows_the_zone_of_now() {
        // 02:00 on the 17th in Dhaka is still the 16th in New York
        val remindAt = millis(dhaka, 2026, 11, 17, 2, 0)
        val nowInNewYork = millis(newYork, 2026, 11, 16, 12, 0)
        // 02:00 in Dhaka (UTC+6) is 15:00 in New York (UTC-5)
        assertEquals("Today, 3:00 PM", ReminderTime.label(remindAt, nowInNewYork, newYork, false))
    }

    // ---- when an alarm is set ----

    @Test
    fun an_alarm_is_set_for_an_open_task_with_a_reminder_to_come() {
        assertTrue(shouldScheduleReminder(remindAt = 2_000L, completed = false, now = 1_000L))
    }

    @Test
    fun no_alarm_for_a_reminder_that_has_passed_or_is_right_now() {
        assertFalse(shouldScheduleReminder(remindAt = 1_000L, completed = false, now = 1_000L))
        assertFalse(shouldScheduleReminder(remindAt = 500L, completed = false, now = 1_000L))
    }

    @Test
    fun no_alarm_for_a_finished_task() {
        assertFalse(shouldScheduleReminder(remindAt = 2_000L, completed = true, now = 1_000L))
    }
}
