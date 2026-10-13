package com.planly.app

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URI

class AiClient(private val context: Context) {
    suspend fun generate(goal: String): List<AiStep> = withContext(Dispatchers.IO) {
        require(goal.trim().length in 3..1500) { "Describe a goal using 3 to 1,500 characters." }
        val p = AppStore.get(context).state.value.preferences
        require(p.aiEnabled) { "Enable AI assistance in Settings." }
        require(AiRules.validEndpoint(p.endpoint)) { "Use your HTTPS server address without /health, /api/plan, credentials, or query parameters." }
        val token = SecretStore(context).read()
        AiRules.validateToken(token)?.let { throw IllegalArgumentException(it) }
        val connection = URI(p.endpoint.trim().trimEnd('/') + "/api/plan").toURL().openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"; connection.connectTimeout = 15_000; connection.readTimeout = 60_000
            connection.instanceFollowRedirects = false; connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $token")
            connection.outputStream.use { it.write(JSONObject().put("goal", goal.trim()).toString().toByteArray(Charsets.UTF_8)) }
            val status = connection.responseCode
            require(status == 200) { when (status) {
                401 -> "The AI server access token is incorrect."
                429 -> "The AI service is busy. Please try again later."
                503 -> "AI server setup is incomplete. Set its Gemini key and a random access token of at least 32 characters, then redeploy. Save the same token in Settings."
                else -> "AI request failed ($status). Please try again."
            } }
            val text = connection.inputStream.use { input ->
                val bytes = java.io.ByteArrayOutputStream(); val buffer = ByteArray(4096)
                while (true) { val n = input.read(buffer); if (n < 0) break
                    require(bytes.size() + n <= 65_536) { "The AI response was too large." }; bytes.write(buffer, 0, n) }
                bytes.toString("UTF-8")
            }
            val a = JSONObject(text).getJSONArray("steps")
            require(a.length() in 1..8) { "The AI returned an invalid plan." }
            (0 until a.length()).map { i -> val t = a.getJSONObject(i)
                val title = t.getString("title").trim(); val minutes = t.getInt("minutes"); val notes = t.optString("notes")
                require(title.length in 1..100 && minutes in 5..120 && notes.length <= 500) { "The AI returned an invalid step." }
                AiStep(title, minutes, notes)
            }
        } finally { connection.disconnect() }
    }
}
