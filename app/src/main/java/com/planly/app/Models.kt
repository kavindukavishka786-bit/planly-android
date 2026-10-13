package com.planly.app

import java.time.*
import java.util.UUID
import java.time.temporal.ChronoUnit

data class ChecklistItem(val id: String = UUID.randomUUID().toString(), val title: String, val done: Boolean = false)
data class FocusSession(val id: String = UUID.randomUUID().toString(), val task: String, val minutes: Int, val finishedAt: Long)

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
    val notifiedAt: Long = 0,
    val repeat: String = "Off",
    val generatedBy: String = "",
    val checklist: List<ChecklistItem> = emptyList(),
    val completedAt: Long = 0
)
data class Preferences(
    val theme: String = "Light", val glass: Boolean = true, val name: String = "",
    val reminders: Boolean = false, val quietHours: Boolean = false,
    val quietStart: Int = 22 * 60, val quietEnd: Int = 7 * 60,
    val aiEnabled: Boolean = false, val endpoint: String = ""
)
data class AppData(val tasks: List<Task> = emptyList(), val preferences: Preferences = Preferences(),
    val focusEnd: Long = 0, val focusRemaining: Long = 25 * 60, val focusTask: String = "",
    val focusDuration: Long = 25 * 60, val sessions: List<FocusSession> = emptyList())
data class AiStep(val title: String, val minutes: Int, val notes: String)

object FocusRules {
    fun finish(data: AppData, now: Long): AppData {
        if (data.focusEnd <= 0 || data.focusEnd > now) return data
        val session = FocusSession(task = data.focusTask, minutes = (data.focusDuration / 60).toInt(), finishedAt = data.focusEnd)
        return data.copy(focusEnd = 0, focusRemaining = 0, sessions = (data.sessions + session).takeLast(365))
    }
}

object AiRules {
    fun validateToken(token: String): String? = when {
        token.length !in 32..512 -> "Use a random server access token of 32 to 512 characters. Set the same token on your server."
        token.any { it.code !in 33..126 } -> "Use an access token without spaces or line breaks."
        else -> null
    }
}

object TaskRules {
    val repeatOptions = listOf("Off", "Daily", "Weekly")
    fun nextOccurrence(task: Task, now: Long, zone: ZoneId = ZoneId.systemDefault()): Task? {
        val days = when (task.repeat) { "Daily" -> 1L; "Weekly" -> 7L; else -> return null }
        var date = Instant.ofEpochMilli(task.dueAt).atZone(zone).plusDays(days)
        val localNow = Instant.ofEpochMilli(now).atZone(zone)
        if (date.toInstant().toEpochMilli() <= now) {
            val gap = ChronoUnit.DAYS.between(date.toLocalDate(), localNow.toLocalDate()).coerceAtLeast(0)
            date = date.plusDays((gap / days) * days)
            if (date.toInstant().toEpochMilli() <= now) date = date.plusDays(days)
        }
        return task.copy(id = UUID.randomUUID().toString(), dueAt = date.toInstant().toEpochMilli(), done = false,
            generatedBy = task.id, snoozedUntil = 0, notifiedAt = 0, completedAt = 0,
            checklist = task.checklist.map { it.copy(id = UUID.randomUUID().toString(), done = false) })
    }
    fun toggleCompletion(tasks: List<Task>, id: String, now: Long, zone: ZoneId = ZoneId.systemDefault()): List<Task> {
        val task = tasks.find { it.id == id } ?: return tasks
        val updated = task.copy(done = !task.done, completedAt = if (task.done) 0 else now, snoozedUntil = 0, notifiedAt = 0)
        val next = tasks.map { if (it.id == id) updated else it }
        val occurrence = if (!task.done && tasks.none { it.generatedBy == id }) nextOccurrence(task, now, zone) else null
        return if (occurrence == null) next else next + occurrence
    }
    fun search(tasks: List<Task>, filter: String, query: String, project: String, priority: String, sort: String, now: Long): List<Task> {
        val term = query.trim()
        val found = tasks.filter { task ->
            val matchesState = when (filter) { "Active" -> !task.done; "Done" -> task.done; "Overdue" -> !task.done && task.dueAt < now; else -> true }
            matchesState && (project == "All projects" || task.project == project) && (priority == "Any priority" || task.priority == priority) &&
                (term.isEmpty() || listOf(task.title, task.notes, task.project).any { it.contains(term, ignoreCase = true) } || task.checklist.any { it.title.contains(term, ignoreCase = true) })
        }
        val comparator = when (sort) {
            "Priority" -> compareBy<Task> { when (it.priority) { "High" -> 0; "Normal" -> 1; else -> 2 } }.thenBy { it.dueAt }
            "Shortest" -> compareBy<Task> { it.minutes }.thenBy { it.dueAt }
            else -> compareBy<Task> { it.dueAt }
        }
        return found.sortedWith(comparator.thenBy { it.id })
    }
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
