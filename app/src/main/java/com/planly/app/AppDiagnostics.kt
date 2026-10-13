package com.planly.app

import android.content.Context
import android.os.Process
import kotlin.system.exitProcess

data class AppIssue(val code: String, val message: String, val at: Long)

object AppDiagnostics {
    private var installed = false
    private fun prefs(context: Context) = context.getSharedPreferences("planly_health", Context.MODE_PRIVATE)
    fun record(context: Context, code: String, message: String) {
        // Callers use fixed messages; never record keys, tokens, task text or server response bodies.
        prefs(context).edit().putString("code", code).putString("message", message).putLong("at", System.currentTimeMillis()).apply()
    }
    fun last(context: Context): AppIssue? {
        val p = prefs(context); val code = p.getString("code", "").orEmpty()
        return if (code.isEmpty()) null else AppIssue(code, p.getString("message", "").orEmpty(), p.getLong("at", 0))
    }
    fun clearIf(context: Context, code: String) { if (last(context)?.code == code) prefs(context).edit().clear().apply() }
    fun clear(context: Context) { prefs(context).edit().clear().apply() }
    @Synchronized fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { prefs(app).edit().putString("code", "APP_CRASH")
                .putString("message", "An unexpected app error occurred. Run the checks below and install the latest update if it happens again.")
                .putLong("at", System.currentTimeMillis()).commit() }
            if (previous != null) previous.uncaughtException(thread, error)
            else { Process.killProcess(Process.myPid()); exitProcess(1) }
        }
    }
}
