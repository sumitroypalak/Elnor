package com.elnor.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class TaskFilterTest {

    private val today = LocalDate.of(2026, 10, 27)
    private val todayDay = today.toEpochDay()

    private fun task(
        id: Long,
        title: String = "t$id",
        createdAt: Long = id,
        due: Long? = null,
        done: Long? = null
    ) = Task(id = id, title = title, createdAt = createdAt, dueDate = due, completedAt = done)

    // ---- Filter ----

    @Test
    fun filter_all_keepsEverything() {
        val tasks = listOf(task(1), task(2, done = 10L))
        assertEquals(listOf(1L, 2L), filterTasks(tasks, TaskFilter.ALL).map { it.id })
    }

    @Test
    fun filter_active_dropsFinishedTasks() {
        val tasks = listOf(task(1), task(2, done = 10L), task(3))
        assertEquals(listOf(1L, 3L), filterTasks(tasks, TaskFilter.ACTIVE).map { it.id })
    }

    @Test
    fun filter_completed_keepsOnlyFinishedTasks() {
        val tasks = listOf(task(1), task(2, done = 10L), task(3, done = 11L))
        assertEquals(listOf(2L, 3L), filterTasks(tasks, TaskFilter.COMPLETED).map { it.id })
    }

    // ---- Sort ----

    @Test
    fun sort_openTasksComeBeforeFinishedTasks() {
        val tasks = listOf(task(1, done = 50L), task(2), task(3, done = 60L), task(4))
        assertEquals(listOf(4L, 2L, 3L, 1L), sortTasks(tasks).map { it.id })
    }

    @Test
    fun sort_nearestDeadlineFirst_noDeadlineLast() {
        val tasks = listOf(
            task(1, due = null),
            task(2, due = todayDay + 5),
            task(3, due = todayDay - 1),
            task(4, due = todayDay)
        )
        assertEquals(listOf(3L, 4L, 2L, 1L), sortTasks(tasks).map { it.id })
    }

    @Test
    fun sort_sameDeadline_newestCreatedFirst_thenById() {
        val tasks = listOf(
            task(1, createdAt = 100L, due = todayDay),
            task(2, createdAt = 300L, due = todayDay),
            task(3, createdAt = 300L, due = todayDay)
        )
        assertEquals(listOf(2L, 3L, 1L), sortTasks(tasks).map { it.id })
    }

    @Test
    fun sort_finishedTasks_mostRecentlyFinishedFirst() {
        val tasks = listOf(task(1, done = 10L), task(2, done = 30L), task(3, done = 20L))
        assertEquals(listOf(2L, 3L, 1L), sortTasks(tasks).map { it.id })
    }

    // ---- Urgency and labels ----

    @Test
    fun urgency_followsTheDeadline() {
        assertEquals(TaskUrgency.NONE, taskUrgency(task(1), today))
        assertEquals(TaskUrgency.OVERDUE, taskUrgency(task(1, due = todayDay - 1), today))
        assertEquals(TaskUrgency.TODAY, taskUrgency(task(1, due = todayDay), today))
        assertEquals(TaskUrgency.SOON, taskUrgency(task(1, due = todayDay + 1), today))
        assertEquals(TaskUrgency.SOON, taskUrgency(task(1, due = todayDay + 2), today))
        assertEquals(TaskUrgency.LATER, taskUrgency(task(1, due = todayDay + 3), today))
    }

    @Test
    fun urgency_finishedTaskIsNeverOverdue() {
        assertEquals(TaskUrgency.NONE, taskUrgency(task(1, due = todayDay - 9, done = 5L), today))
    }

    @Test
    fun formatDueDate_nearDays() {
        assertEquals("Today", formatDueDate(todayDay, today))
        assertEquals("Tomorrow", formatDueDate(todayDay + 1, today))
        assertEquals("Yesterday", formatDueDate(todayDay - 1, today))
    }

    @Test
    fun formatDueDate_sameYearHasNoYear_otherYearHasYear() {
        // 27 Oct 2026 is a Tuesday, so 30 Oct is a Friday
        assertEquals("Fri, 30 Oct", formatDueDate(LocalDate.of(2026, 10, 30).toEpochDay(), today))
        assertEquals("5 Jan 2027", formatDueDate(LocalDate.of(2027, 1, 5).toEpochDay(), today))
    }

    @Test
    fun deadlineLabel_overdueOpenTask_hasPrefix() {
        assertEquals("Overdue · Yesterday", deadlineLabel(task(1, due = todayDay - 1), today))
        assertEquals("Today", deadlineLabel(task(1, due = todayDay), today))
    }

    @Test
    fun deadlineLabel_finishedTask_hasNoOverduePrefix() {
        assertEquals("Yesterday", deadlineLabel(task(1, due = todayDay - 1, done = 5L), today))
    }

    @Test
    fun deadlineLabel_noDeadline_isNull() {
        assertNull(deadlineLabel(task(1), today))
    }

    // ---- Count line ----

    @Test
    fun countLabel_variants() {
        assertEquals("No tasks yet", taskCountLabel(emptyList()))
        assertEquals("All done", taskCountLabel(listOf(task(1, done = 5L))))
        assertEquals("1 task left", taskCountLabel(listOf(task(1), task(2, done = 5L))))
        assertEquals("2 tasks left", taskCountLabel(listOf(task(1), task(2))))
    }
}
