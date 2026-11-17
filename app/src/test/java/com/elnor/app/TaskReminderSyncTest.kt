package com.elnor.app

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The alarms must always follow the tasks: set when due, removed when not. */
class TaskReminderSyncTest {

    private class FakeDao : TaskDao {
        val rows = MutableStateFlow<List<TaskEntity>>(emptyList())
        private var nextId = 1L

        override fun observeAll(): Flow<List<TaskEntity>> = rows

        override suspend fun insert(task: TaskEntity): Long {
            val id = nextId++
            rows.value = rows.value + task.copy(id = id)
            return id
        }

        override suspend fun updateDetails(id: Long, title: String, dueDate: Long?, remindAt: Long?) {
            rows.value = rows.value.map {
                if (it.id == id) it.copy(title = title, dueDate = dueDate, remindAt = remindAt) else it
            }
        }

        override suspend fun getById(id: Long): TaskEntity? = rows.value.firstOrNull { it.id == id }

        override suspend fun withUpcomingReminder(now: Long): List<TaskEntity> =
            rows.value.filter { it.completedAt == null && (it.remindAt ?: 0L) > now }

        override suspend fun setCompletedAt(id: Long, completedAt: Long?) {
            rows.value = rows.value.map { if (it.id == id) it.copy(completedAt = completedAt) else it }
        }

        override suspend fun deleteById(id: Long) {
            rows.value = rows.value.filter { it.id != id }
        }

        override suspend fun deleteCompleted(): Int {
            val n = rows.value.count { it.completedAt != null }
            rows.value = rows.value.filter { it.completedAt == null }
            return n
        }
    }

    /** Remembers the alarms that are set right now, like the phone does. */
    private class FakeScheduler : ReminderScheduler {
        val alarms = mutableMapOf<Long, Pair<String, Long>>()
        override fun schedule(taskId: Long, title: String, atMillis: Long) {
            alarms[taskId] = title to atMillis
        }
        override fun cancel(taskId: Long) {
            alarms.remove(taskId)
        }
    }

    private val now = 10_000L
    private val dao = FakeDao()
    private val scheduler = FakeScheduler()
    private val repo = RoomTaskRepository(dao, { now }, scheduler)

    @Test
    fun adding_a_task_with_a_future_reminder_sets_an_alarm() = runBlocking {
        val id = repo.add("  Call mum ", null, 20_000L)!!
        assertEquals("Call mum" to 20_000L, scheduler.alarms[id])
    }

    @Test
    fun a_task_without_a_reminder_gets_no_alarm() = runBlocking {
        repo.add("Plain", 5L)
        assertTrue(scheduler.alarms.isEmpty())
    }

    @Test
    fun a_reminder_in_the_past_gets_no_alarm() = runBlocking {
        repo.add("Late", null, 9_000L)
        assertTrue(scheduler.alarms.isEmpty())
    }

    @Test
    fun a_blank_title_stores_nothing_and_sets_nothing() = runBlocking {
        assertNull(repo.add("   ", null, 20_000L))
        assertTrue(scheduler.alarms.isEmpty())
    }

    @Test
    fun changing_the_time_replaces_the_alarm() = runBlocking {
        val id = repo.add("A", null, 20_000L)!!
        repo.update(id, "A2", null, 30_000L)
        assertEquals(mapOf(id to ("A2" to 30_000L)), scheduler.alarms)
    }

    @Test
    fun removing_the_reminder_takes_the_alarm_away() = runBlocking {
        val id = repo.add("A", null, 20_000L)!!
        repo.update(id, "A", null, null)
        assertTrue(scheduler.alarms.isEmpty())
    }

    @Test
    fun checking_a_task_off_takes_the_alarm_away_and_opening_it_again_brings_it_back() = runBlocking {
        val id = repo.add("A", null, 20_000L)!!
        repo.setCompleted(id, true)
        assertTrue(scheduler.alarms.isEmpty())
        repo.setCompleted(id, false)
        assertEquals("A" to 20_000L, scheduler.alarms[id])
    }

    @Test
    fun deleting_a_task_takes_the_alarm_away() = runBlocking {
        val id = repo.add("A", null, 20_000L)!!
        repo.delete(id)
        assertTrue(scheduler.alarms.isEmpty())
    }

    @Test
    fun alarms_of_different_tasks_do_not_disturb_each_other() = runBlocking {
        val a = repo.add("A", null, 20_000L)!!
        val b = repo.add("B", null, 30_000L)!!
        repo.delete(a)
        assertEquals(mapOf(b to ("B" to 30_000L)), scheduler.alarms)
    }
}
