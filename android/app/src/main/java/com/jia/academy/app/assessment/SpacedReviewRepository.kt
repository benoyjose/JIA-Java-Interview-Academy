package com.jia.academy.app.assessment
import android.content.Context
import com.jia.academy.app.auth.SecureSessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
data class DueReviewItem(val lessonSlug:String,val title:String,val repetitions:Int,val intervalDays:Int,val dueDate:String)
class SpacedReviewRepository(private val context:Context){
 suspend fun due(baseUrl:String):List<DueReviewItem> = withContext(Dispatchers.IO){
  val token=SecureSessionStore(context).accessToken()?.takeIf{it.isNotBlank()}?:throw IllegalStateException("Sign in to load your review queue.")
  val response=request(baseUrl.trimEnd('/')+"/api/v1/me/review-schedule/due","GET",token,null)
  val items=response.optJSONArray("items")
  (0 until (items?.length()?:0)).map{index->val item=items!!.getJSONObject(index);DueReviewItem(item.optString("slug"),item.optString("title"),item.optInt("repetitions"),item.optInt("interval_days"),item.optString("due_date"))}
 }
 suspend fun grade(baseUrl:String,lessonSlug:String,rating:Int):JSONObject=withContext(Dispatchers.IO){
  require(rating in 0..5)
  val token=SecureSessionStore(context).accessToken()?.takeIf{it.isNotBlank()}?:throw IllegalStateException("Sign in to save a review rating.")
  val slug=URLEncoder.encode(lessonSlug,"UTF-8").replace("+","%20")
  request(baseUrl.trimEnd('/')+"/api/v1/me/review-schedule/"+slug+"/grade","POST",token,JSONObject().put("rating",rating).toString())
 }
 private fun request(url:String,method:String,token:String,body:String?):JSONObject{
  val connection=(URL(url).openConnection() as HttpURLConnection).apply{requestMethod=method;connectTimeout=8000;readTimeout=10000;setRequestProperty("Accept","application/json");setRequestProperty("Authorization","Bearer "+token);if(body!=null){doOutput=true;setRequestProperty("Content-Type","application/json")}}
  try{if(body!=null)connection.outputStream.use{it.write(body.toByteArray(Charsets.UTF_8))};val status=connection.responseCode;val stream=if(status in 200..299)connection.inputStream else connection.errorStream;val payload=stream.bufferedReader().use{it.readText()};if(status !in 200..299)throw IllegalStateException("Review request failed (HTTP "+status+").");return JSONObject(payload)}finally{connection.disconnect()}
 }
}
