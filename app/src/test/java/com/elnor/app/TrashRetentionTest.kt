package com.elnor.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** TrashRetention: the 7 / 30 / 90 days choice of Settings > Trash. */
class TrashRetentionTest {

    @Test
    fun choicesAreSevenThirtyAndNinetyDays() {
        assertEquals(listOf(7, 30, 90), TrashRetention.entries.map { it.days })
    }

    @Test
    fun defaultIsThirtyDays_likeBefore() {
        assertEquals(TrashRetention.DAYS_30, TrashRetention.DEFAULT)
        assertEquals(TRASH_RETENTION_DAYS, TrashRetention.DEFAULT.days)
        assertEquals(TRASH_RETENTION_MS, TrashRetention.DEFAULT.millis)
    }

    @Test
    fun millisAreWholeDays() {
        assertEquals(7L * 86_400_000L, TrashRetention.DAYS_7.millis)
        assertEquals(30L * 86_400_000L, TrashRetention.DAYS_30.millis)
        assertEquals(90L * 86_400_000L, TrashRetention.DAYS_90.millis)
    }

    @Test
    fun savedValuesComeBackAsTheSameChoice() {
        for (choice in TrashRetention.entries) {
            assertEquals(choice, TrashRetention.fromStorage(choice.storageValue))
        }
    }

    @Test
    fun nothingSavedOrUnknownGivesTheDefault() {
        assertEquals(TrashRetention.DEFAULT, TrashRetention.fromStorage(null))
        assertEquals(TrashRetention.DEFAULT, TrashRetention.fromStorage(""))
        assertEquals(TrashRetention.DEFAULT, TrashRetention.fromStorage("45"))
        assertEquals(TrashRetention.DEFAULT, TrashRetention.fromStorage("DAYS_7"))
    }

    @Test
    fun labels() {
        assertEquals("7 days", TrashRetention.DAYS_7.label())
        assertEquals("30 days", TrashRetention.DAYS_30.label())
        assertEquals("90 days", TrashRetention.DAYS_90.label())
    }

    @Test
    fun isShorterThan() {
        assertTrue(TrashRetention.DAYS_7.isShorterThan(TrashRetention.DAYS_30))
        assertTrue(TrashRetention.DAYS_30.isShorterThan(TrashRetention.DAYS_90))
        assertFalse(TrashRetention.DAYS_30.isShorterThan(TrashRetention.DAYS_30))
        assertFalse(TrashRetention.DAYS_90.isShorterThan(TrashRetention.DAYS_7))
    }
}
