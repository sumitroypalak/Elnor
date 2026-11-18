package com.elnor.app

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CollectionsTest {

    private fun col(id: Long, name: String, createdAt: Long = id) = NoteCollection(id, name, createdAt)

    private fun note(id: Long, collection: Long?, updatedAt: Long = id) =
        Note(id, "n$id", "", updatedAt, collectionId = collection)

    private fun task(id: Long, collection: Long?, done: Boolean = false, due: Long? = null) =
        Task(id, "t$id", createdAt = id, dueDate = due, completedAt = if (done) 1L else null, collectionId = collection)

    // ---- names ----

    @Test
    fun clean_trims_and_joins_spaces() {
        assertEquals("Home work", CollectionNames.clean("   Home    work \n"))
    }

    @Test
    fun a_normal_name_is_fine_also_in_bengali() {
        assertNull(CollectionNames.problem("Work", emptyList()))
        assertNull(CollectionNames.problem("বাজার", emptyList()))
    }

    @Test
    fun a_blank_name_is_refused() {
        assertEquals(CollectionNameProblem.BLANK, CollectionNames.problem("", emptyList()))
        assertEquals(CollectionNameProblem.BLANK, CollectionNames.problem("  \n ", emptyList()))
    }

    @Test
    fun a_name_that_is_too_long_is_refused() {
        assertNull(CollectionNames.problem("x".repeat(CollectionNames.MAX_LENGTH), emptyList()))
        assertEquals(
            CollectionNameProblem.TOO_LONG,
            CollectionNames.problem("x".repeat(CollectionNames.MAX_LENGTH + 1), emptyList())
        )
    }

    @Test
    fun the_length_is_counted_after_cleaning() {
        val padded = "  " + "x".repeat(CollectionNames.MAX_LENGTH) + "   "
        assertNull(CollectionNames.problem(padded, emptyList()))
    }

    @Test
    fun a_name_that_exists_is_refused_whatever_the_capitals_and_spaces() {
        val existing = listOf(col(1, "Home Work"))
        assertEquals(CollectionNameProblem.DUPLICATE, CollectionNames.problem("home   work", existing))
        assertNull(CollectionNames.problem("Work", existing))
    }

    @Test
    fun the_two_ways_of_typing_a_bengali_letter_are_one_name() {
        val oneCharacter = "য়" // য় as one character
        val twoCharacters = "য়" // য + ় as two
        assertEquals(
            CollectionNameProblem.DUPLICATE,
            CollectionNames.problem(twoCharacters, listOf(col(1, oneCharacter)))
        )
    }

    @Test
    fun renaming_a_collection_to_its_own_name_with_other_capitals_is_fine() {
        val existing = listOf(col(1, "work"), col(2, "Home"))
        assertNull(CollectionNames.problem("Work", existing, exceptId = 1))
        assertEquals(CollectionNameProblem.DUPLICATE, CollectionNames.problem("Home", existing, exceptId = 1))
    }

    @Test
    fun every_problem_has_a_message() {
        for (p in CollectionNameProblem.entries) assertTrue(CollectionNames.message(p).isNotBlank())
    }

    // ---- the list ----

    @Test
    fun collections_are_sorted_by_name_ignoring_capitals() {
        val sorted = sortCollections(listOf(col(1, "beta"), col(2, "Alpha"), col(3, "Gamma")))
        assertEquals(listOf(2L, 1L, 3L), sorted.map { it.id })
    }

    @Test
    fun the_older_one_comes_first_when_the_names_look_the_same() {
        val sorted = sortCollections(listOf(col(5, "a", createdAt = 50), col(6, "A", createdAt = 10)))
        assertEquals(listOf(6L, 5L), sorted.map { it.id })
    }

    // ---- counts ----

    @Test
    fun counts_notes_and_tasks_per_collection() {
        val counts = countByCollection(
            notes = listOf(note(1, 10), note(2, 10), note(3, 20), note(4, null)),
            tasks = listOf(task(1, 10), task(2, 30, done = true), task(3, null))
        )
        assertEquals(CollectionCounts(2, 1), counts[10])
        assertEquals(CollectionCounts(1, 0), counts[20])
        assertEquals(CollectionCounts(0, 1), counts[30]) // a finished task counts too
        assertEquals(3, counts.size)
    }

    @Test
    fun counts_of_nothing_are_empty() {
        assertTrue(countByCollection(emptyList(), emptyList()).isEmpty())
    }

    @Test
    fun the_label_of_the_counts() {
        assertEquals("Empty", collectionCountsLabel(null))
        assertEquals("Empty", collectionCountsLabel(CollectionCounts(0, 0)))
        assertEquals("1 note", collectionCountsLabel(CollectionCounts(1, 0)))
        assertEquals("3 notes", collectionCountsLabel(CollectionCounts(3, 0)))
        assertEquals("1 task", collectionCountsLabel(CollectionCounts(0, 1)))
        assertEquals("2 tasks", collectionCountsLabel(CollectionCounts(0, 2)))
        assertEquals("3 notes · 2 tasks", collectionCountsLabel(CollectionCounts(3, 2)))
        assertEquals("1 note · 1 task", collectionCountsLabel(CollectionCounts(1, 1)))
    }

    @Test
    fun the_notes_of_a_collection_are_newest_first() {
        val notes = listOf(note(1, 10, updatedAt = 5), note(2, 10, updatedAt = 9), note(3, 20), note(4, null))
        assertEquals(listOf(2L, 1L), notesInCollection(notes, 10).map { it.id })
        assertTrue(notesInCollection(notes, 99).isEmpty())
    }

    @Test
    fun the_tasks_of_a_collection_use_the_order_of_the_task_tab() {
        val tasks = listOf(
            task(1, 10, done = true),
            task(2, 10, due = null),
            task(3, 10, due = 100L),
            task(4, 20)
        )
        // open tasks with a deadline first, then open tasks without, finished tasks last
        assertEquals(listOf(3L, 2L, 1L), tasksInCollection(tasks, 10).map { it.id })
    }

    @Test
    fun collectionName_finds_the_name_or_null() {
        val list = listOf(col(1, "Work"))
        assertEquals("Work", collectionName(list, 1))
        assertNull(collectionName(list, 2))
        assertNull(collectionName(list, null))
    }

    // ---- the repository ----

    private class FakeDao : CollectionDao() {
        val rows = MutableStateFlow<List<CollectionEntity>>(emptyList())
        val cleared = mutableListOf<String>()
        private var nextId = 1L

        override fun observeAll(): Flow<List<CollectionEntity>> = rows
        override suspend fun getAll(): List<CollectionEntity> = rows.value
        override suspend fun insert(collection: CollectionEntity): Long {
            val id = nextId++
            rows.value = rows.value + collection.copy(id = id)
            return id
        }
        override suspend fun rename(id: Long, name: String) {
            rows.value = rows.value.map { if (it.id == id) it.copy(name = name) else it }
        }
        override suspend fun clearNotes(id: Long) { cleared.add("notes$id") }
        override suspend fun clearTasks(id: Long) { cleared.add("tasks$id") }
        override suspend fun deleteRow(id: Long) {
            cleared.add("row$id")
            rows.value = rows.value.filter { it.id != id }
        }
    }

    private val dao = FakeDao()
    private val repo = RoomCollectionRepository(dao) { 7L }

    @Test
    fun create_stores_the_cleaned_name() = runBlocking {
        val id = repo.create("  Home   work ")!!
        assertEquals(CollectionEntity(id, "Home work", 7L), dao.rows.value.single())
    }

    @Test
    fun create_refuses_a_bad_name_and_stores_nothing() = runBlocking {
        assertNull(repo.create("   "))
        repo.create("Work")
        assertNull(repo.create("work"))
        assertEquals(1, dao.rows.value.size)
    }

    @Test
    fun rename_changes_the_name_and_refuses_a_name_that_exists() = runBlocking {
        val a = repo.create("A")!!
        repo.create("B")
        assertTrue(repo.rename(a, " Alpha "))
        assertEquals("Alpha", dao.rows.value.first { it.id == a }.name)
        assertFalse(repo.rename(a, "b"))
        assertEquals("Alpha", dao.rows.value.first { it.id == a }.name)
    }

    @Test
    fun rename_of_a_collection_that_is_gone_does_nothing() = runBlocking {
        assertFalse(repo.rename(99, "X"))
        assertTrue(dao.rows.value.isEmpty())
    }

    @Test
    fun delete_takes_the_notes_and_tasks_out_first_then_removes_the_row() = runBlocking {
        val id = repo.create("A")!!
        repo.delete(id)
        assertEquals(listOf("notes$id", "tasks$id", "row$id"), dao.cleared)
        assertTrue(dao.rows.value.isEmpty())
    }
}
