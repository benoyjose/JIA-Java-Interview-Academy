package com.jia.academy.app.assessment

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class JiaQuizQuestion(
    val position: Int,
    val questionType: String,
    val prompt: String,
    val codeLanguage: String?,
    val codeSource: String?,
    val options: List<Pair<String, String>>,
    val hint: String?
)
data class JiaQuizAttempt(val id: String, val difficulty: String, val questions: List<JiaQuizQuestion>)
data class JiaQuizResult(val questionCount: Int, val correctCount: Int, val scorePercent: Double, val explanations: Map<Int, Pair<Boolean, String>>)
data class JiaQuizHistoryItem(val id: String, val difficulty: String, val questionCount: Int, val correctCount: Int?, val scorePercent: Double?, val status: String)
data class JiaQuizPerformanceLevel(val difficulty: String, val attempts: Int, val questionsAttempted: Int, val correctAnswers: Int, val accuracyPercent: Double?, val bestScorePercent: Double?)
data class JiaQuizPerformance(val levels: List<JiaQuizPerformanceLevel>)

class QuizRepository {
    private fun endpoint(baseUrl: String, path: String) = baseUrl.trimEnd('/') + "/api/v1" + path
    private fun request(baseUrl: String, path: String, token: String, method: String, body: JSONObject? = null): String {
        val connection = (URL(endpoint(baseUrl, path)).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10000
            readTimeout = 15000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        try {
            if (body != null) connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body.toString()) }
            val status = connection.responseCode
            val response = (if (status in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val message = runCatching { JSONObject(response).let { it.optString("message").ifBlank { it.optString("detail") } } }.getOrNull()
                error(message?.takeIf(String::isNotBlank) ?: "Quiz request failed (HTTP $status).")
            }
            return response
        } finally { connection.disconnect() }
    }

    suspend fun start(baseUrl: String, token: String, difficulty: String, count: Int = 10): JiaQuizAttempt =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("difficulty", difficulty).put("questionCount", count).put("mode", "PRACTICE")
            val json = JSONObject(request(baseUrl, "/quiz-attempts", token, "POST", body))
            val questionsJson = json.getJSONArray("questions")
            val questions = (0 until questionsJson.length()).map { index ->
                val q = questionsJson.getJSONObject(index)
                val prompt = q.optJSONObject("prompt")
                val optionsJson = q.optJSONArray("options") ?: JSONArray()
                val options = (0 until optionsJson.length()).map { n ->
                    val option = optionsJson.getJSONObject(n)
                    option.optString("value") to option.optString("label", option.optString("value"))
                }
                val code = prompt?.optJSONObject("code")
                JiaQuizQuestion(q.getInt("position"), q.getString("questionType"),
                    prompt?.optString("text").orEmpty(),
                    code?.optString("language")?.takeIf(String::isNotBlank),
                    code?.optString("source")?.takeIf(String::isNotBlank),
                    options, q.optString("hint").takeIf(String::isNotBlank))
            }
            JiaQuizAttempt(json.getString("attemptId"), json.getString("difficulty"), questions)
        }

    suspend fun submit(baseUrl: String, token: String, attempt: JiaQuizAttempt, answers: Map<Int, Set<String>>): JiaQuizResult =
        withContext(Dispatchers.IO) {
            val rows = JSONArray()
            attempt.questions.forEach { question ->
                val selected = answers[question.position].orEmpty()
                val response = JSONObject()
                if (question.questionType == "MULTI_SELECT") {
                    val values = JSONArray()
                    selected.sorted().forEach(values::put)
                    response.put("values", values)
                } else {
                    selected.firstOrNull()?.let { response.put("value", it) }
                }
                rows.put(JSONObject().put("position", question.position).put("response", response))
            }
            val result = JSONObject(request(baseUrl, "/quiz-attempts/${attempt.id}/submit", token, "POST",
                JSONObject().put("answers", rows)))
            val answerRows = result.optJSONArray("answers") ?: JSONArray()
            val explanations = (0 until answerRows.length()).associate { i ->
                val item = answerRows.getJSONObject(i)
                item.getInt("position") to (item.optBoolean("isCorrect") to item.optString("explanation"))
            }
            JiaQuizResult(result.getInt("questionCount"), result.getInt("correctCount"),
                result.getDouble("scorePercent"), explanations)
        }

    suspend fun performance(baseUrl: String, token: String): JiaQuizPerformance = withContext(Dispatchers.IO) {
        val json = JSONObject(request(baseUrl, "/me/quiz-performance", token, "GET"))
        val array = json.optJSONArray("byDifficulty") ?: JSONArray()
        val levels = (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            JiaQuizPerformanceLevel(item.getString("difficulty"), item.getInt("attempts"),
                item.getInt("questionsAttempted"), item.getInt("correctAnswers"),
                if (item.isNull("accuracyPercent")) null else item.getDouble("accuracyPercent"),
                if (item.isNull("bestScorePercent")) null else item.getDouble("bestScorePercent"))
        }
        JiaQuizPerformance(levels)
    }

    suspend fun history(baseUrl: String, token: String): List<JiaQuizHistoryItem> = withContext(Dispatchers.IO) {
        val array = JSONArray(request(baseUrl, "/me/quiz-attempts?limit=20", token, "GET"))
        (0 until array.length()).map { i ->
            val item = array.getJSONObject(i)
            JiaQuizHistoryItem(item.getString("attemptId"), item.getString("difficulty"),
                item.getInt("questionCount"), if (item.isNull("correctCount")) null else item.getInt("correctCount"),
                if (item.isNull("scorePercent")) null else item.getDouble("scorePercent"), item.getString("status"))
        }
    }
}
