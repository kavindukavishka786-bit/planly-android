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
                    snoozedUntil = t.optLong("snooze"), notifiedAt = t.optLong("notified"))
            }
        }.getOrElse { emptyList() }
        return AppData(tasks, Preferences(theme = prefs.getString("theme", "Light") ?: "Light",
            glass = prefs.getBoolean("glass", true), name = prefs.getString("name", "") ?: "",
            reminders = prefs.getBoolean("reminders", false), quietHours = prefs.getBoolean("quiet", false),
            quietStart = prefs.getInt("quietStart", 1320), quietEnd = prefs.getInt("quietEnd", 420),
            aiEnabled = prefs.getBoolean("ai", false), endpoint = prefs.getString("endpoint", "") ?: ""),
            prefs.getLong("focusEnd", 0), prefs.getLong("focusRemaining", 1500), prefs.getString("focusTask", "") ?: "")
    }
    @Synchronized private fun save(next: AppData) {
        val a = JSONArray()
        next.tasks.forEach { t -> a.put(JSONObject().put("id", t.id).put("title", t.title)
            .put("notes", t.notes).put("project", t.project).put("dueAt", t.dueAt).put("minutes", t.minutes)
            .put("priority", t.priority).put("reminder", t.reminderMinutes).put("done", t.done)
            .put("snooze", t.snoozedUntil).put("notified", t.notifiedAt)) }
        val p = next.preferences
        prefs.edit().putString("tasks", a.toString()).putString("theme", p.theme).putBoolean("glass", p.glass)
            .putString("name", p.name).putBoolean("reminders", p.reminders).putBoolean("quiet", p.quietHours)
            .putInt("quietStart", p.quietStart).putInt("quietEnd", p.quietEnd).putBoolean("ai", p.aiEnabled)
            .putString("endpoint", p.endpoint).putLong("focusEnd", next.focusEnd)
            .putLong("focusRemaining", next.focusRemaining).putString("focusTask", next.focusTask).apply()
        mutable.value = next
    }
    @Synchronized fun put(task: Task) {
        require(TaskRules.validate(task.title, task.minutes) == null)
        val old = mutable.value.tasks.find { it.id == task.id }
        val timingChanged = old != null && (old.dueAt != task.dueAt || old.reminderMinutes != task.reminderMinutes)
        val next = if (timingChanged) task.copy(snoozedUntil = 0, notifiedAt = 0) else task
        save(mutable.value.copy(tasks = mutable.value.tasks.filterNot { it.id == task.id } + next))
        Reminders.schedule(context, next, mutable.value.preferences)
    }
    @Synchronized fun toggle(id: String) {
        mutable.value.tasks.find { it.id == id }?.let { put(it.copy(done = !it.done, snoozedUntil = 0, notifiedAt = 0)) }
        Reminders.cancelNotification(context, id)
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
        if (d.focusEnd > 0) save(d.copy(focusEnd = 0, focusRemaining = ((d.focusEnd - System.currentTimeMillis()) / 1000).coerceAtLeast(0)))
        else save(d.copy(focusTask = task, focusEnd = System.currentTimeMillis() + d.focusRemaining.coerceAtLeast(1) * 1000))
    }
    @Synchronized fun resetFocus(task: String = mutable.value.focusTask) { save(mutable.value.copy(focusEnd = 0, focusRemaining = 1500, focusTask = task)) }
    @Synchronized fun finishFocus() { save(mutable.value.copy(focusEnd = 0, focusRemaining = 0)) }
    companion object {
        @Volatile private var instance: AppStore? = null
        fun get(context: Context): AppStore = instance ?: synchronized(this) {
            instance ?: AppStore(context.applicationContext as Application).also { instance = it }
        }
    }
}
