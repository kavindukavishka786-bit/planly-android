package com.planly.app

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class AdvancedRulesTest {
    private val zone = ZoneId.of("Asia/Colombo")
    private fun at(day: String, hour: Int = 9) = LocalDate.parse(day).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()

    @Test fun dailyRepeatPreservesTimeAndClearsCompletionAndChecklistState() {
        val task = Task(title = "Study", dueAt = at("2026-10-13"), repeat = "Daily", notifiedAt = 50,
            snoozedUntil = 100, completedAt = 200, checklist = listOf(ChecklistItem(title = "Read notes", done = true)))
        val next = TaskRules.nextOccurrence(task, at("2026-10-13", 10), zone)!!
        assertEquals(at("2026-10-14"), next.dueAt)
        assertEquals(task.id, next.generatedBy)
        assertNotEquals(task.id, next.id)
        assertFalse(next.done); assertFalse(next.checklist.single().done)
        assertNotEquals(task.checklist.single().id, next.checklist.single().id)
        assertEquals(0L, next.notifiedAt); assertEquals(0L, next.snoozedUntil); assertEquals(0L, next.completedAt)
    }
    @Test fun lateCompletionSkipsMissedWeeklyOccurrences() {
        val next = TaskRules.nextOccurrence(Task(title = "Plan", dueAt = at("2026-09-29"), repeat = "Weekly"), at("2026-10-13", 10), zone)!!
        assertEquals(at("2026-10-20"), next.dueAt)
    }
    @Test fun repeatUsesLocalWallTimeAcrossDaylightSaving() {
        val ny = ZoneId.of("America/New_York")
        val before = LocalDate.of(2026, 10, 31).atTime(9, 0).atZone(ny).toInstant().toEpochMilli()
        val next = TaskRules.nextOccurrence(Task(title = "Read", dueAt = before, repeat = "Daily"), before, ny)!!
        val local = Instant.ofEpochMilli(next.dueAt).atZone(ny)
        assertEquals(LocalDate.of(2026, 11, 1), local.toLocalDate()); assertEquals(9, local.hour)
        assertEquals(25 * 60 * 60_000L, next.dueAt - before)
    }
    @Test fun togglingCompletionAgainDoesNotDuplicateTheNextOccurrence() {
        val task = Task(title = "Study", dueAt = at("2026-10-13"), repeat = "Daily")
        val completed = TaskRules.toggleCompletion(listOf(task), task.id, at("2026-10-13", 10), zone)
        assertEquals(2, completed.size); assertTrue(completed.first().done)
        val undone = TaskRules.toggleCompletion(completed, task.id, at("2026-10-13", 11), zone)
        val again = TaskRules.toggleCompletion(undone, task.id, at("2026-10-13", 12), zone)
        assertEquals(2, again.size); assertEquals(completed.last().id, again.last().id)
        assertEquals(at("2026-10-13", 12), again.first().completedAt)
    }
    @Test fun ordinaryCompletionDoesNotCreateRepeatsAndUndoClearsStatistics() {
        val task = Task(title = "Read", dueAt = at("2026-10-13"))
        val done = TaskRules.toggleCompletion(listOf(task), task.id, at("2026-10-13", 10), zone)
        assertEquals(1, done.size)
        assertEquals(0L, TaskRules.toggleCompletion(done, task.id, at("2026-10-13", 11), zone).single().completedAt)
    }
    @Test fun searchFindsNotesAndChecklistsAndCombinesFilters() {
        val tasks = listOf(Task(title = "Presentation", notes = "Quantum computing", project = "Studies", priority = "High", dueAt = 100,
            checklist = listOf(ChecklistItem(title = "Make diagrams"))), Task(title = "Shopping", project = "Personal", dueAt = 50))
        assertEquals("Presentation", TaskRules.search(tasks, "Active", " quantum ", "Studies", "High", "Due date", 200).single().title)
        assertEquals("Presentation", TaskRules.search(tasks, "Overdue", "DIAGRAMS", "All projects", "Any priority", "Due date", 200).single().title)
        assertTrue(TaskRules.search(tasks, "Active", "quantum", "Personal", "Any priority", "Due date", 200).isEmpty())
    }
    @Test fun sortAndOverdueFilteringAreStable() {
        val a = Task(id = "a", title = "Later", priority = "High", dueAt = 300, minutes = 45)
        val b = Task(id = "b", title = "Earlier", priority = "Low", dueAt = 100, minutes = 15)
        val c = b.copy(id = "c", title = "Completed", done = true)
        val tasks = listOf(c, a, b)
        assertEquals(listOf("a", "b"), TaskRules.search(tasks, "Active", "", "All projects", "Any priority", "Priority", 200).map { it.id })
        assertEquals(listOf("b", "a"), TaskRules.search(tasks, "Active", "", "All projects", "Any priority", "Shortest", 200).map { it.id })
        assertEquals(listOf("b"), TaskRules.search(tasks, "Overdue", "", "All projects", "Any priority", "Due date", 200).map { it.id })
    }
    @Test fun finishingFocusRecordsTheDeadlineOnceAndKeepsTheSelectedDuration() {
        val d = AppData(focusEnd = 1000, focusDuration = 45 * 60L, focusTask = "Study")
        val finished = FocusRules.finish(d, 2000)
        assertEquals(45, finished.sessions.single().minutes); assertEquals(1000L, finished.sessions.single().finishedAt)
        assertEquals(finished, FocusRules.finish(finished, 3000)); assertEquals(0L, finished.focusEnd)
        assertEquals(d, FocusRules.finish(d, 999))
        assertTrue(FocusRules.finish(d.copy(focusEnd = 0), 2000).sessions.isEmpty())
    }
}
