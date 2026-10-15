package com.elnor.app

import org.junit.Assert.assertEquals
import org.junit.Test

class NotesViewModeTest {

    @Test
    fun fromStorage_roundTripsEveryMode() {
        for (mode in NotesViewMode.entries) {
            assertEquals(mode, NotesViewMode.fromStorage(mode.storageValue))
        }
    }

    @Test
    fun fromStorage_defaultsToList() {
        assertEquals(NotesViewMode.LIST, NotesViewMode.fromStorage(null))
        assertEquals(NotesViewMode.LIST, NotesViewMode.fromStorage("unknown"))
    }

    @Test
    fun toggled_switchesBetweenTheTwoViews() {
        assertEquals(NotesViewMode.GRID, NotesViewMode.LIST.toggled())
        assertEquals(NotesViewMode.LIST, NotesViewMode.GRID.toggled())
    }
}
