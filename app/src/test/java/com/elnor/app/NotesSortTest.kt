package com.elnor.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotesSortTest {

    private fun note(
        id: Long,
        title: String,
        updatedAt: Long,
        pinnedAt: Long? = null,
        body: String = ""
    ) = Note(id = id, title = title, body = body, updatedAt = updatedAt, pinnedAt = pinnedAt)

    // ids: 1 Banana (10), 2 apple (20), 3 Cherry (30, pinned 5), 4 no title "zebra line" (40),
    // 5 Dates (50, pinned 9)
    private val notes = listOf(
        note(1, "Banana", 10),
        note(2, "apple", 20),
        note(3, "Cherry", 30, pinnedAt = 5),
        note(4, "", 40, body = "zebra line\nmore"),
        note(5, "Dates", 50, pinnedAt = 9)
    )

    private fun ids(sort: NotesSort, list: List<Note> = notes) =
        sortNotes(list, sort).map { it.id }

    @Test
    fun newestFirst_pinnedOnTopMostRecentlyPinnedFirst() {
        assertEquals(listOf(5L, 3L, 4L, 2L, 1L), ids(NotesSort.EDITED_NEWEST))
    }

    @Test
    fun oldestFirst_pinnedStillOnTop() {
        assertEquals(listOf(5L, 3L, 1L, 2L, 4L), ids(NotesSort.EDITED_OLDEST))
    }

    @Test
    fun titleAToZ_ignoresCase_andUsesTheNameShownInTheList() {
        // apple, Banana, "zebra line" (the first line is the name of a note without a title)
        assertEquals(listOf(5L, 3L, 2L, 1L, 4L), ids(NotesSort.TITLE_AZ))
    }

    @Test
    fun titleZToA() {
        assertEquals(listOf(5L, 3L, 4L, 1L, 2L), ids(NotesSort.TITLE_ZA))
    }

    @Test
    fun pinnedOrderDoesNotDependOnTheSort() {
        for (sort in NotesSort.entries) {
            assertEquals(listOf(5L, 3L), ids(sort).take(2))
        }
    }

    @Test
    fun sameTitle_newestFirst_thenById() {
        val same = listOf(
            note(7, "Same", 10),
            note(6, "same", 30),
            note(9, "SAME", 10)
        )
        assertEquals(listOf(6L, 7L, 9L), ids(NotesSort.TITLE_AZ, same))
        assertEquals(listOf(6L, 7L, 9L), ids(NotesSort.TITLE_ZA, same))
    }

    @Test
    fun bengaliTitlesAreSortedByLetter() {
        val bengali = listOf(
            note(1, "গ", 1),
            note(2, "ক", 2),
            note(3, "খ", 3)
        )
        assertEquals(listOf(2L, 3L, 1L), ids(NotesSort.TITLE_AZ, bengali))
    }

    @Test
    fun emptyListAndTheInputIsNotChanged() {
        assertTrue(sortNotes(emptyList(), NotesSort.TITLE_AZ).isEmpty())
        val before = notes.toList()
        sortNotes(notes, NotesSort.TITLE_ZA)
        assertEquals(before, notes)
    }

    @Test
    fun fromStorage_roundTripsEverySort() {
        for (sort in NotesSort.entries) {
            assertEquals(sort, NotesSort.fromStorage(sort.storageValue))
        }
    }

    @Test
    fun fromStorage_defaultsToNewestFirst() {
        assertEquals(NotesSort.EDITED_NEWEST, NotesSort.fromStorage(null))
        assertEquals(NotesSort.EDITED_NEWEST, NotesSort.fromStorage("unknown"))
    }

    @Test
    fun selectionTitle_usesSingularAndPlural() {
        assertEquals("1 note selected", selectionTitle(1))
        assertEquals("3 notes selected", selectionTitle(3))
    }
}
