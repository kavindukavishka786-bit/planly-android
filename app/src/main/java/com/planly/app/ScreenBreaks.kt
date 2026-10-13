package com.planly.app

import android.app.AlarmManager
import android.app.KeyguardManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

object ScreenBreaks {
    private const val NOTIFICATION_ID = 204
    private var scheduledAt = 0L
    private var scheduledExact = false
    private fun prefs(context: Context) = context.getSharedPreferences("planly_breaks", Context.MODE_PRIVATE)
    fun nextAt(context: Context) = prefs(context).getLong("nextAt", 0)
    @Synchronized fun resetCache() { scheduledAt = 0 }
    private fun pending(context: Context, action: String = "BREAK_REMIND", flags: Int = PendingIntent.FLAG_UPDATE_CURRENT): PendingIntent? =
        PendingIntent.getBroadcast(context, 0, Intent(context, ScreenBreakReceiver::class.java).setAction(action)
            .setData(Uri.parse("planly://screen-break/$action")), flags or PendingIntent.FLAG_IMMUTABLE)
    @Synchronized fun sync(context: Context, settings: Preferences): Boolean = runCatching {
        val alarm = context.getSystemService(AlarmManager::class.java)
        if (!settings.screenBreaks) {
            pending(context, flags = PendingIntent.FLAG_NO_CREATE)?.let { alarm.cancel(it); it.cancel() }
            prefs(context).edit().remove("nextAt").apply(); scheduledAt = 0
            NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
            return@runCatching true
        }
        val at = BreakRules.nextAt(nextAt(context), System.currentTimeMillis(), settings) ?: return@runCatching true
        val exact = Build.VERSION.SDK_INT < 31 || alarm.canScheduleExactAlarms()
        if (scheduledAt == at && scheduledExact == exact) return@runCatching true
        prefs(context).edit().putLong("nextAt", at).apply()
        val intent = pending(context) ?: return@runCatching false
        try {
            if (exact) alarm.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
            else alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        } catch (_: SecurityException) { alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent) }
        scheduledAt = at; scheduledExact = exact
        true
    }.getOrElse { AppDiagnostics.record(context, "REMINDER_SCHEDULING", "Android blocked screen-break reminders. Review alarm and battery settings, then run Fix issues."); false }

    @Synchronized fun deliver(context: Context, settings: Preferences) {
        if (!settings.screenBreaks) { sync(context, settings); return }
        val now = System.currentTimeMillis()
        // Reject duplicate or early broadcasts before advancing the schedule.
        val expected = nextAt(context)
        if (expected <= 0 || expected > now + 1000) return
        resetCache()
        val deferred = TaskRules.afterQuietHours(now, settings)
        if (deferred > now) {
            prefs(context).edit().putLong("nextAt", deferred).apply(); sync(context, settings); return
        }
        Reminders.channels(context)
        val unlocked = context.getSystemService(PowerManager::class.java).isInteractive &&
            !context.getSystemService(KeyguardManager::class.java).isKeyguardLocked
        if (unlocked && Reminders.ready(context, Reminders.BREAK_CHANNEL)) {
            val open = PendingIntent.getActivity(context, 204, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification = NotificationCompat.Builder(context, Reminders.BREAK_CHANNEL).setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("Time for a screen break").setContentText("Look away, stretch, and take a short pause.")
                .setContentIntent(open).setAutoCancel(true).setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .addAction(0, "Turn off hourly reminders", pending(context, "BREAK_OFF")).build()
            runCatching { NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification) }
        }
        prefs(context).edit().putLong("nextAt", now + BreakRules.HOUR).apply()
        sync(context, settings)
    }
}

class ScreenBreakReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = AppStore.get(context)
        when (intent.action) {
            "BREAK_OFF" -> store.preferences(store.state.value.preferences.copy(screenBreaks = false))
            "BREAK_REMIND" -> ScreenBreaks.deliver(context, store.state.value.preferences)
        }
    }
}
