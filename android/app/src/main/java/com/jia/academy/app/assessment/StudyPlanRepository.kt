package com.jia.academy.app.assessment
import android.content.Context
import com.jia.academy.app.auth.SecureSessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
class StudyPlanRepository(private val context: Context) {
 suspend fun load(baseUrl: String, dailyMinutes: Int = 30): JiaStudyPlan = withContext(Dispatchers.IO) {
  val token = SecureSessionStore(context).accessToken()?.takeIf { it.isNotBlank() }
   ?: throw IllegalStateException("Sign in to generate your personalized study plan.")
  val url = baseUrl.trimEnd('/') + "/api/v1/me/study-plan?dailyMinutes=" + StudyPlanBudget.clamp(dailyMinutes)
  val connection = (URL(url).openConnection() as HttpURLConnection).apply {
   requestMethod = "GET"; connectTimeout = 8000; readTimeout = 10000
   setRequestProperty("Accept", "application/json"); setRequestProperty("Authorization", "Bearer " + token)
  }
  try {
   val status = connection.responseCode
   val stream = if (status in 200..299) connection.inputStream else connection.errorStream
   val payload = stream.bufferedReader().use { it.readText() }
   if (status !in 200..299) throw IllegalStateException("Study plan request failed (HTTP " + status + ").")
   val json = JSONObject(payload); val tasks = json.optJSONArray("tasks")
   val parsed = (0 until (tasks?.length() ?: 0)).map { index ->
    val item = tasks!!.getJSONObject(index)
    JiaStudyPlanTask(item.optString("kind"), item.optString("title"),
     item.optString("lessonSlug").takeIf { it.isNotBlank() && it != "null" },
     item.optString("topicSlug").takeIf { it.isNotBlank() && it != "null" },
     item.optInt("minutes", 10), item.optString("reason"))
   }
   JiaStudyPlan(StudyPlanBudget.clamp(json.optInt("dailyMinutes", dailyMinutes)), StudyPlanBudget.allocate(parsed, dailyMinutes))
  } finally { connection.disconnect() }
 }
}
