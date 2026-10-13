package com.planly.app

import org.junit.Assert.*
import org.junit.Test
import java.time.*

class TaskRulesTest {
    @Test fun validatesTaskInputs() {
        assertNotNull(TaskRules.validate("  ", 15))
        assertNotNull(TaskRules.validate("Read", -1))
        assertNull(TaskRules.validate("Prepare presentation", 45))
    }
    @Test fun overnightQuietHoursCrossMidnight() {
        assertTrue(TaskRules.isQuiet(LocalTime.of(23, 0), 1320, 420))
        assertTrue(TaskRules.isQuiet(LocalTime.of(6, 59), 1320, 420))
        assertFalse(TaskRules.isQuiet(LocalTime.of(7, 0), 1320, 420))
        assertFalse(TaskRules.isQuiet(LocalTime.NOON, 1320, 420))
    }
    @Test fun defersToMorningOfCorrectDay() {
        val zone = ZoneId.of("Asia/Colombo")
        val at = LocalDate.of(2026, 10, 13).atTime(23, 0).atZone(zone).toInstant().toEpochMilli()
        val expected = LocalDate.of(2026, 10, 14).atTime(7, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(expected, TaskRules.afterQuietHours(at, Preferences(quietHours = true), zone))
    }
    @Test fun dayQuietHoursStayOnSameDay() {
        val zone = ZoneId.of("UTC")
        val at = LocalDate.of(2026, 10, 13).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
        val expected = LocalDate.of(2026, 10, 13).atTime(14, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(expected, TaskRules.afterQuietHours(at, Preferences(quietHours = true, quietStart = 660, quietEnd = 840), zone))
    }
    @Test fun futureTaskWithinReminderWindowGetsANudge() {
        val now = 1_800_000_000_000L
        val task = Task(title = "Read", dueAt = now + 5 * 60_000, reminderMinutes = 10)
        assertEquals(now + 1000, TaskRules.reminderTime(task, Preferences(reminders = true), now))
    }
    @Test fun completedAndAlreadyNotifiedTasksDoNotReplay() {
        val now = 1_800_000_000_000L
        val task = Task(title = "Read", dueAt = now + 60 * 60_000)
        val prefs = Preferences(reminders = true)
        assertNull(TaskRules.reminderTime(task.copy(done = true), prefs, now))
        assertNull(TaskRules.reminderTime(task.copy(notifiedAt = now - 1000), prefs, now))
        assertNull(TaskRules.reminderTime(task.copy(dueAt = now - 1000), prefs, now))
    }
    @Test fun overdueSnoozedTaskRemainsScheduled() {
        val now = 1_800_000_000_000L
        val task = Task(title = "Read", dueAt = now - 60_000, snoozedUntil = now + 600_000)
        assertEquals(now + 600_000, TaskRules.reminderTime(task, Preferences(reminders = true), now))
    }
}
