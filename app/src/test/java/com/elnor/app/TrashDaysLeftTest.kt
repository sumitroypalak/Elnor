package com.elnor.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** TrashDaysLeft: the "Deleted for good in N days" line of a note in the Trash. */
class TrashDaysLeftTest {

    private val day = 86_400_000L
    private val thirtyDays = TrashRetention.DAYS_30.millis

    @Test
    fun justDeleted_hasTheWholeTimeLeft() {
        assertEquals(30, TrashDaysLeft.daysLeft(deletedAt = 1_000L, now = 1_000L, retentionMs = thirtyDays))
    }

    @Test
    fun daysAreCountedUp_neverZeroWhileTheNoteIsStillThere() {
        val deletedAt = 0L
        // 12 hours left -> 1 day
        assertEquals(1, TrashDaysLeft.daysLeft(deletedAt, thirtyDays - day / 2, thirtyDays))
        // exactly 1 day left -> 1 day
        assertEquals(1, TrashDaysLeft.daysLeft(deletedAt, thirtyDays - day, thirtyDays))
        // 1 day and 1 ms left -> 2 days
        assertEquals(2, TrashDaysLeft.daysLeft(deletedAt, thirtyDays - day - 1L, thirtyDays))
    }

    @Test
    fun whenTheTimeIsOver_zeroDaysLeft_andNeverNegative() {
        assertEquals(0, TrashDaysLeft.daysLeft(0L, thirtyDays, thirtyDays))
        assertEquals(0, TrashDaysLeft.daysLeft(0L, thirtyDays + 5 * day, thirtyDays))
    }

    @Test
    fun worksForTheOtherChoices() {
        assertEquals(7, TrashDaysLeft.daysLeft(0L, 0L, TrashRetention.DAYS_7.millis))
        assertEquals(90, TrashDaysLeft.daysLeft(0L, 0L, TrashRetention.DAYS_90.millis))
        assertEquals(87, TrashDaysLeft.daysLeft(0L, 3 * day, TrashRetention.DAYS_90.millis))
    }

    @Test
    fun clockGoingBackwards_doesNotShowMoreThanTheChosenTime() {
        // deletedAt in the "future" (phone clock was set back): at most the chosen 30 days
        val days = TrashDaysLeft.daysLeft(deletedAt = 10 * day, now = 0L, retentionMs = thirtyDays)
        assertEquals(30, days)
    }

    @Test
    fun labels() {
        assertEquals("Deleted for good in 12 days", TrashDaysLeft.label(0L, thirtyDays - 12 * day, thirtyDays))
        assertEquals("Deleted for good tomorrow", TrashDaysLeft.label(0L, thirtyDays - day, thirtyDays))
        assertEquals("Will be deleted for good today", TrashDaysLeft.label(0L, thirtyDays, thirtyDays))
    }

    @Test
    fun urgentWhenThreeDaysOrLessAreLeft() {
        assertTrue(TrashDaysLeft.isUrgent(0L, thirtyDays - 3 * day, thirtyDays))
        assertTrue(TrashDaysLeft.isUrgent(0L, thirtyDays, thirtyDays))
        assertFalse(TrashDaysLeft.isUrgent(0L, thirtyDays - 4 * day, thirtyDays))
    }
}
