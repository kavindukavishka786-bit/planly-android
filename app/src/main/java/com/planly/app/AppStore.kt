package com.planly.app

import android.content.Context
import android.app.Application
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class AppStore private constructor(private val context: Application) {
    private val prefs = context.getSharedPreferences("planly", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(read())
    val state = mutable.asStateFlow()
    private fun read(): AppData {
        val tasks = runCatching {
            val a = JSONArray(prefs.getString("tasks", "[]"))
            (0 until a.length()).map { i -> val t = a.getJSONObject(i)
                Task(id = t.getString("id"), title = t.getString("title"), notes = t.optString("notes"),
                    project = t.optString("project", "Personal"), dueAt = t.getLong("dueAt"),
                    minutes = t.optInt("minutes", 15), priority = t.optString("priority", "Normal"),
                    reminderMinutes = t.optInt("reminder", 10), done = t.optBoolean("done"),
                    snoozedUntil = t.optLong("snooze"), notifiedAt = t.optLong("notified"),
                    repeat = t.optString("repeat", "Off").takeIf { it in TaskRules.repeatOptions } ?: "Off",
                    generatedBy = t.optString("generatedBy"), completedAt = t.optLong("completedAt"),
                    checklist = t.optJSONArray("checklist")?.let { items -> (0 until items.length()).map { n ->
                        val item = items.getJSONObject(n)
                        ChecklistItem(id = item.getString("id"), title = item.getString("title"), done = item.optBoolean("done"))
                    } } ?: emptyList())
            }
        }.getOrElse { emptyList() }
        return AppData(tasks, Preferences(theme = prefs.getString("theme", "Light") ?: "Light",
            glass = prefs.getBoolean("glass", true), name = prefs.getString("name", "") ?: "",
            reminders = prefs.getBoolean("reminders", false), quietHours = prefs.getBoolean("quiet", false),
            quietStart = prefs.getInt("quietStart", 1320), quietEnd = prefs.getInt("quietEnd", 420),
            aiEnabled = prefs.getBoolean("ai", false), endpoint = prefs.getString("endpoint", "") ?: ""),
            prefs.getLong("focusEnd", 0), prefs.getLong("focusRemaining", 1500), prefs.getString("focusTask", "") ?: "",
            prefs.getLong("focusDuration", 1500), runCatching {
                val a = JSONArray(prefs.getString("sessions", "[]"))
                (0 until a.length()).map { i -> val s = a.getJSONObject(i)
                    FocusSession(s.getString("id"), s.optString("task"), s.getInt("minutes"), s.getLong("finishedAt"))
                }
            }.getOrElse { emptyList() })
    }
    @Synchronized private fun save(next: AppData) {
        val edit = prefs.edit()
        // Appearance and timer changes avoid reserializing the entire task database.
        if (next.tasks != mutable.value.tasks) {
            val a = JSONArray()
            next.tasks.forEach { t -> a.put(JSONObject().put("id", t.id).put("title", t.title)
                .put("notes", t.notes).put("project", t.project).put("dueAt", t.dueAt).put("minutes", t.minutes)
                .put("priority", t.priority).put("reminder", t.reminderMinutes).put("done", t.done)
                .put("snooze", t.snoozedUntil).put("notified", t.notifiedAt).put("repeat", t.repeat)
                .put("generatedBy", t.generatedBy).put("completedAt", t.completedAt)
                .put("checklist", JSONArray().apply { t.checklist.forEach { item ->
                    put(JSONObject().put("id", item.id).put("title", item.title).put("done", item.done))
                } })) }
            edit.putString("tasks", a.toString())
        }
        if (next.sessions != mutable.value.sessions) {
            val sessions = JSONArray().apply { next.sessions.forEach { s ->
                put(JSONObject().put("id", s.id).put("task", s.task).put("minutes", s.minutes).put("finishedAt", s.finishedAt))
            } }
            edit.putString("sessions", sessions.toString())
        }
        val p = next.preferences
        edit.putString("theme", p.theme).putBoolean("glass", p.glass)
            .putString("name", p.name).putBoolean("reminders", p.reminders).putBoolean("quiet", p.quietHours)
            .putInt("quietStart", p.quietStart).putInt("quietEnd", p.quietEnd).putBoolean("ai", p.aiEnabled)
            .putString("endpoint", p.endpoint).putLong("focusEnd", next.focusEnd)
            .putLong("focusRemaining", next.focusRemaining).putString("focusTask", next.focusTask)
            .putLong("focusDuration", next.focusDuration).apply()
        mutable.value = next
    }
    @Synchronized fun put(task: Task) {
        require(TaskRules.validate(task.title, task.minutes) == null)
        require(task.repeat in TaskRules.repeatOptions)
        require(task.checklist.size <= 20 && task.checklist.all { it.title.isNotBlank() && it.title.length <= 100 })
        val old = mutable.value.tasks.find { it.id == task.id }
        val timingChanged = old != null && (old.dueAt != task.dueAt || old.reminderMinutes != task.reminderMinutes)
        val next = if (timingChanged) task.copy(snoozedUntil = 0, notifiedAt = 0) else task
        save(mutable.value.copy(tasks = mutable.value.tasks.filterNot { it.id == task.id } + next))
        Reminders.schedule(context, next, mutable.value.preferences)
    }
    @Synchronized fun toggle(id: String) {
        val old = mutable.value.tasks
        val next = TaskRules.toggleCompletion(old, id, System.currentTimeMillis())
        save(mutable.value.copy(tasks = next))
        next.filter { it.id == id || (it.generatedBy == id && old.none { previous -> previous.id == it.id }) }
            .forEach { Reminders.schedule(context, it, mutable.value.preferences) }
        Reminders.cancelNotification(context, id)
    }
    @Synchronized fun toggleChecklist(taskId: String, itemId: String) {
        val task = mutable.value.tasks.find { it.id == taskId } ?: return
        val updated = task.copy(checklist = task.checklist.map { if (it.id == itemId) it.copy(done = !it.done) else it })
        save(mutable.value.copy(tasks = mutable.value.tasks.map { if (it.id == taskId) updated else it }))
    }
    @Synchronized fun remove(id: String) {
        Reminders.cancel(context, id)
        save(mutable.value.copy(tasks = mutable.value.tasks.filterNot { it.id == id }))
    }
    @Synchronized fun preferences(p: Preferences) {
        val old = mutable.value.preferences
        save(mutable.value.copy(preferences = p))
        if (old.reminders != p.reminders || old.quietHours != p.quietHours || old.quietStart != p.quietStart || old.quietEnd != p.quietEnd) reschedule()
    }
    @Synchronized fun reschedule() { mutable.value.tasks.forEach { Reminders.schedule(context, it, mutable.value.preferences) } }
    @Synchronized fun markNotified(id: String) {
        save(mutable.value.copy(tasks = mutable.value.tasks.map { if (it.id == id) it.copy(notifiedAt = System.currentTimeMillis(), snoozedUntil = 0) else it }))
    }
    @Synchronized fun snooze(id: String) {
        mutable.value.tasks.find { it.id == id }?.let { put(it.copy(snoozedUntil = System.currentTimeMillis() + 600_000, notifiedAt = 0)) }
        Reminders.cancelNotification(context, id)
    }
    @Synchronized fun focus(task: String = mutable.value.focusTask) {
        val d = mutable.value
        if (d.focusEnd > 0 && d.focusEnd <= System.currentTimeMillis()) { finishFocus(); return }
        if (d.focusEnd > 0) save(d.copy(focusEnd = 0, focusRemaining = ((d.focusEnd - System.currentTimeMillis()) / 1000).coerceAtLeast(0)))
        else save(d.copy(focusTask = task, focusEnd = System.currentTimeMillis() + d.focusRemaining.coerceAtLeast(1) * 1000))
    }
    @Synchronized fun resetFocus(task: String = mutable.value.focusTask) { save(mutable.value.copy(focusEnd = 0, focusRemaining = mutable.value.focusDuration, focusTask = task)) }
    @Synchronized fun focusPreset(minutes: Int) {
        require(minutes in listOf(15, 25, 45))
        if (mutable.value.focusEnd > 0) return
        save(mutable.value.copy(focusDuration = minutes * 60L, focusRemaining = minutes * 60L))
    }
    @Synchronized fun finishFocus(): Boolean {
        val d = mutable.value
        val next = FocusRules.finish(d, System.currentTimeMillis())
        if (next == d) return false
        save(next)
        return true
    }
    companion object {
        @Volatile private var instance: AppStore? = null
        fun get(context: Context): AppStore = instance ?: synchronized(this) {
            instance ?: AppStore(context.applicationContext as Application).also { instance = it }
        }
    }
}
