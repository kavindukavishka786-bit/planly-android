package com.planly.app

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

object Reminders {
    const val CHANNEL = "tasks"
    fun channels(context: Context) {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Task reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Gentle reminders for your scheduled tasks"
            })
    }
    private fun pending(context: Context, id: String, action: String = "REMIND", flags: Int = PendingIntent.FLAG_UPDATE_CURRENT): PendingIntent? =
        PendingIntent.getBroadcast(context, 0, Intent(context, ReminderReceiver::class.java)
            .setAction(action).setData(Uri.parse("planly://task/$id/$action")).putExtra("id", id), flags or PendingIntent.FLAG_IMMUTABLE)
    fun cancelNotification(context: Context, id: String) { NotificationManagerCompat.from(context).cancel(id, 1) }
    private fun cancelAlarm(context: Context, id: String) {
        pending(context, id, flags = PendingIntent.FLAG_NO_CREATE)?.let {
            context.getSystemService(AlarmManager::class.java).cancel(it); it.cancel()
        }
    }
    fun cancel(context: Context, id: String) {
        cancelAlarm(context, id)
        cancelNotification(context, id)
    }
    fun schedule(context: Context, task: Task, prefs: Preferences) {
        cancelAlarm(context, task.id)
        if (!prefs.reminders || task.done || task.reminderMinutes < 0) { cancelNotification(context, task.id); return }
        if (task.notifiedAt > 0) return
        // Preserve quiet-hour deferrals across restart. A newly scheduled task
        // already inside its reminder window gets a nudge while still future.
        val at = TaskRules.reminderTime(task, prefs, System.currentTimeMillis()) ?: return
        val alarm = context.getSystemService(AlarmManager::class.java)
        val intent = pending(context, task.id) ?: return
        try {
            if (Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms()) alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
            else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        } catch (_: SecurityException) { alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent) }
    }
    fun notify(context: Context, task: Task): Boolean {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return false
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        channels(context)
        val launch = PendingIntent.getActivity(context, task.id.hashCode(), Intent(context, MainActivity::class.java)
            .putExtra("taskId", task.id).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(com.planly.app.R.drawable.ic_notification)
            .setContentTitle(task.title).setContentText("A little nudge for your next step.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(task.notes.ifBlank { "Take one small step at a time." }))
            .setContentIntent(launch).setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .addAction(0, "Mark done", pending(context, task.id, "DONE"))
            .addAction(0, "Snooze 10 min", pending(context, task.id, "SNOOZE")).build()
        try { NotificationManagerCompat.from(context).notify(task.id, 1, notification); return true }
        catch (_: SecurityException) { return false }
    }
}
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        val store = AppStore.get(context)
        val task = store.state.value.tasks.find { it.id == id } ?: return
        when (intent.action) {
            "DONE" -> if (!task.done) store.toggle(id)
            "SNOOZE" -> if (!task.done) store.snooze(id)
            "REMIND" -> {
                val p = store.state.value.preferences
                if (task.done || !p.reminders || task.reminderMinutes < 0 || task.notifiedAt > 0) return
                val now = System.currentTimeMillis()
                val delayed = TaskRules.afterQuietHours(now, p)
                if (delayed > now) { Reminders.schedule(context, task.copy(snoozedUntil = delayed), p); return }
                if (Reminders.notify(context, task)) store.markNotified(id)
            }
        }
    }
}
class RescheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val allowed = setOf(Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIME_CHANGED, Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED, "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED")
        if (intent.action in allowed) AppStore.get(context).reschedule()
    }
}
