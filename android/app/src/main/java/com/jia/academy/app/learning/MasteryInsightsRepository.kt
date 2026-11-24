package com.jia.academy.app.learning
import android.content.Context
import com.jia.academy.app.auth.SecureSessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class MasteryInsightsRepository(private val context:Context) {
 suspend fun load(baseUrl:String):List<TopicMastery> = withContext(Dispatchers.IO) {
  val token=SecureSessionStore(context).accessToken()?.takeIf{it.isNotBlank()}
   ?: throw IllegalStateException("Sign in to load mastery evidence.")
  val connection=(URL(baseUrl.trimEnd('/')+"/api/v1/me/mastery").openConnection() as HttpURLConnection).apply {
   requestMethod="GET";connectTimeout=8000;readTimeout=10000
   setRequestProperty("Accept","application/json");setRequestProperty("Authorization","Bearer "+token)
  }
  try {
   val status=connection.responseCode
   val stream=if(status in 200..299)connection.inputStream else connection.errorStream
   val text=stream.bufferedReader().use{it.readText()}
   if(status !in 200..299)throw IllegalStateException("Mastery request failed (HTTP $status).")
   val items=JSONObject(text).optJSONArray("topics")
   (0 until (items?.length()?:0)).map{index->val item=items!!.getJSONObject(index)
    TopicMastery(item.optString("topicSlug"),item.optString("topicName"),item.optInt("answers"),item.optInt("correct"),item.optInt("completedLessons"))}
  } finally {connection.disconnect()}
 }
}