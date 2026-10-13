package com.planly.app

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private suspend fun testAiServer(endpoint: String, token: String): String = withContext(Dispatchers.IO) {
    if (!AiRules.validEndpoint(endpoint)) return@withContext "Enter only your HTTPS server address in AI Settings."
    AiRules.validateToken(token)?.let { return@withContext it }
    val connection = URI(endpoint.trim().trimEnd('/') + "/api/plan").toURL().openConnection() as HttpURLConnection
    try {
        connection.instanceFollowRedirects = false
        connection.requestMethod = "POST"; connection.doOutput = true
        connection.connectTimeout = 15_000; connection.readTimeout = 75_000
        connection.setRequestProperty("Authorization", "Bearer $token")
        connection.setRequestProperty("Content-Type", "application/json")
        // An empty goal tests authentication and configuration without invoking Gemini or adding tasks.
        connection.outputStream.use { it.write("{\"goal\":\"\"}".toByteArray()) }
        when (connection.responseCode) {
            400 -> "Server connected and access token accepted. Create a plan to test Gemini."
            401 -> "Access token mismatch. Save the same token in PLANLY and your server."
            503 -> "Server setup is incomplete. Set GEMINI_API_KEY and APP_ACCESS_TOKEN on the server, then redeploy."
            429 -> "Server is busy. Wait a minute and test again."
            301, 302, 307, 308 -> "Use the final HTTPS server address; redirects are blocked to protect your token."
            else -> "Server did not respond as expected. Check its deployment and URL."
        }
    } finally { connection.disconnect() }
}

@Composable fun AppHealthScreen(prefs: Preferences, permissionsVersion: Int, onNotifications: () -> Unit, onAiSettings: () -> Unit, onMessage: (String) -> Unit) {
    val context = LocalContext.current
    val p = LocalPlanly.current
    val scope = rememberCoroutineScope()
    var refresh by remember { mutableIntStateOf(0) }
    var busy by remember { mutableStateOf(false) }
    var fixing by remember { mutableStateOf(false) }
    var serverResult by remember { mutableStateOf<String?>(null) }
    val manager = context.getSystemService(NotificationManager::class.java)
    val notifications = remember(refresh, permissionsVersion) { Reminders.ready(context, Reminders.CHANNEL) }
    val taskChannel = remember(refresh, permissionsVersion) { manager.getNotificationChannel(Reminders.CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE }
    val breakChannel = remember(refresh, permissionsVersion) { manager.getNotificationChannel(Reminders.BREAK_CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE }
    val exact = remember(refresh, permissionsVersion) { Build.VERSION.SDK_INT < 31 || context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms() }
    val battery = remember(refresh, permissionsVersion) { context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName) }
    val token = remember(refresh, prefs.endpoint) { runCatching { SecretStore(context).read() }.getOrDefault("") }
    val aiConfigured = AiRules.validEndpoint(prefs.endpoint) && AiRules.validateToken(token) == null
    val issue = remember(refresh, permissionsVersion) { AppDiagnostics.last(context) }
    fun open(intent: Intent) { runCatching { context.startActivity(intent) }.onFailure { onMessage("Open Android Settings → Apps → PLANLY to review this setting.") } }
    fun channel(id: String) = open(Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName).putExtra(Settings.EXTRA_CHANNEL_ID, id))
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("A little care for your app.", color = p.text, fontSize = 27.sp, lineHeight = 33.sp, fontWeight = FontWeight.Bold)
            Text("Check settings, restore reminders, and see what needs attention.", color = p.secondaryText, fontSize = 13.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 10.dp))
            Button(enabled = !fixing, onClick = {
                fixing = true
                scope.launch {
                    try {
                        val result = withContext(Dispatchers.IO) { runCatching { AppStore.get(context).reschedule(force = true) } }
                        refresh++
                        onMessage(if (result.getOrDefault(1) == 0) "Reminder schedules rebuilt. Review any highlighted settings below." else "Android still blocks scheduling. Review alarm and battery settings below.")
                    } finally { fixing = false }
                }
            }, modifier = Modifier.fillMaxWidth().padding(top = 16.dp)) { Text(if (fixing) "Restoring reminders…" else "Fix issues") }
            OutlinedButton(onClick = { refresh++ }, modifier = Modifier.fillMaxWidth()) { Text("Run checks") }
        }
        item { HealthCard("Notifications", if (notifications) "Task notifications can be delivered." else "Allow notifications and enable the task reminder channel.", notifications,
            "Open notification settings", { open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) })
            if (!notifications) TextButton(onClick = onNotifications) { Text("Request notification permission") }
        }
        item { HealthCard("Task reminder channel", if (taskChannel) "Channel is enabled. Task reminders are ${if (prefs.reminders) "on" else "off in PLANLY Settings"}." else "This channel is blocked by Android.", taskChannel,
            "Review task channel", { channel(Reminders.CHANNEL) }) }
        item { HealthCard("Hourly screen breaks", if (breakChannel) "Channel is enabled. Hourly breaks are ${if (prefs.screenBreaks) "on" else "off in PLANLY Settings"}." else "Screen-break notifications are blocked by Android.", breakChannel,
            "Review screen-break channel", { channel(Reminders.BREAK_CHANNEL) }) }
        item { HealthCard("Alarm timing", if (exact) "Precise alarm access is available." else "Standard reminders can arrive late. Allow precise alarms if timing matters.", exact,
            if (exact) null else "Allow precise alarms", { if (Build.VERSION.SDK_INT >= 31) open(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))) }) }
        item { HealthCard("Battery settings", if (battery) "Android battery optimization is unrestricted for PLANLY." else "Android manages background activity. If reminders arrive late, review Battery and background activity in App info.", battery,
            "Review battery settings", { open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))) }) }
        item { HealthCard("AI setup", if (!prefs.aiEnabled) "AI assistance is off. You can enable it in Settings." else if (aiConfigured) "Local server address and token format look correct." else "Check your HTTPS server address and 32–512 character token.", !prefs.aiEnabled || aiConfigured,
            "Open AI settings", onAiSettings) }
        item {
            Button(enabled = !busy && prefs.aiEnabled && aiConfigured, onClick = {
                busy = true; serverResult = null
                scope.launch {
                    try { serverResult = testAiServer(prefs.endpoint, token) }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { serverResult = "Could not reach the server. Check internet access; a sleeping server may need a minute to wake." }
                    finally { busy = false }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text(if (busy) "Checking server…" else "Test AI connection") }
            serverResult?.let { Text(it, color = p.secondaryText, fontSize = 13.sp, lineHeight = 20.sp, modifier = Modifier.padding(top = 8.dp)) }
        }
        item {
            OutlinedButton(onClick = {
                onMessage(if (Reminders.test(context)) "Test reminder sent. Check your notification shade." else "Notifications are blocked. Allow permission and enable the task reminder channel first.")
                refresh++
            }, modifier = Modifier.fillMaxWidth()) { Text("Send test reminder") }
        }
        if (issue != null) item {
            HealthCard("Last reported issue", issue.message + "\n" + Instant.ofEpochMilli(issue.at).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM, h:mm a")), false,
                "Clear notice", { AppDiagnostics.clear(context); refresh++ })
        }
        item {
            OutlinedButton(onClick = {
                val report = "PLANLY 0.4.0 · Android API ${Build.VERSION.SDK_INT}\nNotifications ready: $notifications\nTask channel: $taskChannel\nBreak channel: $breakChannel\nPrecise alarms: $exact\nBattery unrestricted: $battery\nHourly breaks enabled: ${prefs.screenBreaks}\nAI local setup valid: $aiConfigured\nLast issue code: ${issue?.code ?: "None"}"
                context.getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("PLANLY diagnostics", report))
                onMessage("Diagnostic report copied. It contains no keys, tokens or task text.")
            }, modifier = Modifier.fillMaxWidth()) { Text("Copy diagnostic report") }
            Text("Fix issues restores app-controlled schedules. Android permissions, battery restrictions and server credentials need your review. No tasks are deleted.", color = p.secondaryText, fontSize = 11.sp, lineHeight = 18.sp, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable private fun HealthCard(title: String, detail: String, good: Boolean, action: String? = null, onAction: () -> Unit = {}) {
    val p = LocalPlanly.current
    Surface(color = if (good) p.mint else p.peach, shape = RoundedCornerShape(22.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(18.dp)) {
            Text(title, color = p.text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Text(detail, color = p.secondaryText, fontSize = 12.sp, lineHeight = 19.sp, modifier = Modifier.padding(top = 8.dp))
            if (action != null) TextButton(onClick = onAction) { Text(action) }
        }
    }
}
