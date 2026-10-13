package com.planly.app

import java.time.*
import java.util.UUID

data class Task(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val notes: String = "",
    val project: String = "Personal",
    val dueAt: Long,
    val minutes: Int = 15,
    val priority: String = "Normal",
    val reminderMinutes: Int = 10,
    val done: Boolean = false,
    val snoozedUntil: Long = 0,
    val notifiedAt: Long = 0
)
data class Preferences(
    val theme: String = "Light", val glass: Boolean = true, val name: String = "",
    val reminders: Boolean = false, val quietHours: Boolean = false,
    val quietStart: Int = 22 * 60, val quietEnd: Int = 7 * 60,
    val aiEnabled: Boolean = false, val endpoint: String = ""
)
data class AppData(val tasks: List<Task> = emptyList(), val preferences: Preferences = Preferences(),
    val focusEnd: Long = 0, val focusRemaining: Long = 25 * 60, val focusTask: String = "")
data class AiStep(val title: String, val minutes: Int, val notes: String)

object TaskRules {
    fun validate(title: String, minutes: Int): String? = when {
        title.trim().isEmpty() -> "Give your task a name."
        title.trim().length > 100 -> "Keep task names under 100 characters."
        minutes !in 5..480 -> "Choose an estimate between 5 and 480 minutes."
        else -> null
    }
    fun isQuiet(time: LocalTime, start: Int, end: Int): Boolean {
        if (start == end) return false
        val minute = time.hour * 60 + time.minute
        return if (start < end) minute in start until end else minute >= start || minute < end
    }
    fun afterQuietHours(at: Long, prefs: Preferences, zone: ZoneId = ZoneId.systemDefault()): Long {
        val local = Instant.ofEpochMilli(at).atZone(zone)
        if (!prefs.quietHours || !isQuiet(local.toLocalTime(), prefs.quietStart, prefs.quietEnd)) return at
        val endTime = LocalTime.of(prefs.quietEnd / 60, prefs.quietEnd % 60)
        var date = local.toLocalDate()
        if (local.toLocalTime() >= endTime) date = date.plusDays(1)
        return date.atTime(endTime).atZone(zone).toInstant().toEpochMilli()
    }
    fun dayOf(task: Task): LocalDate = Instant.ofEpochMilli(task.dueAt).atZone(ZoneId.systemDefault()).toLocalDate()
    fun reminderTime(task: Task, prefs: Preferences, now: Long, zone: ZoneId = ZoneId.systemDefault()): Long? {
        if (!prefs.reminders || task.done || task.reminderMinutes < 0 || task.notifiedAt > 0) return null
        val raw = if (task.snoozedUntil > 0) task.snoozedUntil else task.dueAt - task.reminderMinutes * 60_000L
        val deferred = afterQuietHours(raw, prefs, zone)
        return if (deferred > now) deferred else if (task.dueAt > now && task.snoozedUntil == 0L)
            afterQuietHours(now + 1000, prefs, zone) else null
    }
}
