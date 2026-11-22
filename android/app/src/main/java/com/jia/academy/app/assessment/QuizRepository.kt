package com.jia.academy.app.assessment

import com.jia.academy.app.api.ApiErrorMessage
import android.content.Context
import com.jia.academy.app.auth.SecureSessionStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

enum class JiaQuizMode(val apiValue: String, val displayName: String, val availableToLearners: Boolean) {
    PRACTICE("PRACTICE", "Practice", true),
    TIMED_MOCK("TIMED_MOCK", "Timed mock interview", true),
    DAILY_CHALLENGE("DAILY_CHALLENGE", "Daily challenge", true),
    WEAK_AREA("WEAK_AREA", "Weak-area practice", true);

    companion object {
        val learnerOptions: List<JiaQuizMode> = values().filter { it.availableToLearners }
        val timedDurationOptionsSeconds: List<Int> = listOf(900, 1800, 3600)
        const val defaultTimedDurationSeconds = 1800

        /** Non-timed modes omit duration from the API; invalid timed values use the documented default. */
        fun durationSecondsFor(mode: JiaQuizMode, requestedSeconds: Int): Int? =
            if (mode == TIMED_MOCK) requestedSeconds.takeIf(timedDurationOptionsSeconds::contains)
                ?: defaultTimedDurationSeconds else null
    }
}

data class JiaQuizQuestion(
    val position: Int,
    val questionType: String,
    val prompt: String,
    val codeLanguage: String?,
    val codeSource: String?,
    val options: List<Pair<String, String>>,
    val hint: String?
)
data class JiaQuizAttempt(
    val id: String, val difficulty: String, val questions: List<JiaQuizQuestion>,
    val mode: JiaQuizMode, val deadlineAt: String?, val durationLimitSeconds: Int?, val challengeDate: String?
)
data class JiaQuizResult(val questionCount: Int, val correctCount: Int, val scorePercent: Double,
    val explanations: Map<Int, Pair<Boolean, String>>, val timedOut: Boolean = false)
data class JiaQuizHistoryItem(val id: String, val difficulty: String, val questionCount: Int, val correctCount: Int?, val scorePercent: Double?, val status: String)
data class JiaQuizPerformanceLevel(val difficulty: String, val attempts: Int, val questionsAttempted: Int, val correctAnswers: Int, val accuracyPercent: Double?, val bestScorePercent: Double?)
data class JiaQuizPerformance(val levels: List<JiaQuizPerformanceLevel>)

data class JiaLessonProgress(
    val lessonSlug: String, val contentRevision: Int, val status: String,
    val progressPercent: Double, val updatedAt: String
)
data class JiaLessonBookmark(val lessonSlug: String, val title: String, val createdAt: String)


class QuizRepository(private val context: Context? = null) {
    private val refreshLock = Any()
    private fun endpoint(baseUrl: String, path: String) = baseUrl.trimEnd('/') + "/api/v1" + path
    private fun request(baseUrl: String, path: String, token: String, method: String, body: JSONObject? = null,
                        allowRefresh: Boolean = true): String {
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
            if (status == 401 && allowRefresh && context != null) {
                val rotatedToken = rotateAccessToken(baseUrl, token)
                if (!rotatedToken.isNullOrBlank()) {
                    return request(baseUrl, path, rotatedToken, method, body, allowRefresh = false)
                }
            }
            if (status !in 200..299) {
                error(ApiErrorMessage.from(response, "Quiz request failed", status))
            }
            return response
        } finally { connection.disconnect() }
    }

    private fun rotateAccessToken(baseUrl: String, rejectedAccessToken: String): String? {
        return synchronized(refreshLock) {
            val appContext = context ?: return@synchronized null
            val store = SecureSessionStore(appContext)
            val currentAccessToken = store.accessToken()
            if (!currentAccessToken.isNullOrBlank() && currentAccessToken != rejectedAccessToken) {
                return@synchronized currentAccessToken
            }
            val refreshToken = store.refreshToken() ?: return@synchronized null
            val connection = (URL(endpoint(baseUrl, "/auth/refresh")).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 10_000
                readTimeout = 15_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }
            try {
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use {
                    it.write(JSONObject().put("refreshToken", refreshToken).toString())
                }
                if (connection.responseCode !in 200..299) return@synchronized null
                val json = JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
                val user = json.getJSONObject("user")
                val accessToken = json.getString("accessToken")
                store.save(accessToken, json.getString("refreshToken"), json.getString("expiresAt"), user.toString())
                accessToken
            } catch (_: Exception) {
                null
            } finally { connection.disconnect() }
        }
    }

    suspend fun start(baseUrl: String, token: String, difficulty: String, count: Int = 10,
        mode: JiaQuizMode = JiaQuizMode.PRACTICE, durationSeconds: Int = 1800): JiaQuizAttempt =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("difficulty", difficulty).put("questionCount", count).put("mode", mode.apiValue)
            if (mode == JiaQuizMode.TIMED_MOCK) body.put("durationSeconds", durationSeconds)
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
            JiaQuizAttempt(json.getString("attemptId"), json.getString("difficulty"), questions, mode,
                json.optString("deadlineAt").takeIf { it.isNotBlank() && it != "null" },
                if (json.isNull("durationLimitSeconds")) null else json.optInt("durationLimitSeconds"),
                json.optString("challengeDate").takeIf { it.isNotBlank() && it != "null" })
        }

    suspend fun saveAnswers(baseUrl: String, token: String, attempt: JiaQuizAttempt, answers: Map<Int, Set<String>>, timeSpentMs: Map<Int, Long> = emptyMap()) =
        withContext(Dispatchers.IO) {
            val rows = JSONArray()
            attempt.questions.forEach { question ->
                val selected = answers[question.position].orEmpty()
                if (selected.isNotEmpty()) {
                    val response = JSONObject()
                    if (question.questionType == "MULTI_SELECT") {
                        val values = JSONArray()
                        selected.sorted().forEach(values::put)
                        response.put("values", values)
                    } else selected.firstOrNull()?.let { response.put("value", it) }
                    rows.put(JSONObject().put("position", question.position).put("response", response)
                        .put("timeSpentMs", timeSpentMs[question.position]?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt() ?: 0))
                }
            }
            if (rows.length() > 0) request(baseUrl, "/quiz-attempts/${attempt.id}/answers", token, "POST",
                JSONObject().put("answers", rows))
        }

    suspend fun submit(baseUrl: String, token: String, attempt: JiaQuizAttempt, answers: Map<Int, Set<String>>, timeSpentMs: Map<Int, Long> = emptyMap()): JiaQuizResult =
        withContext(Dispatchers.IO) {
            val rows = JSONArray()
            attempt.questions.forEach { question ->
                val selected = answers[question.position].orEmpty()
                if (selected.isNotEmpty()) {
                val response = JSONObject()
                if (question.questionType == "MULTI_SELECT") {
                    val values = JSONArray()
                    selected.sorted().forEach(values::put)
                    response.put("values", values)
                } else {
                    selected.firstOrNull()?.let { response.put("value", it) }
                }
                rows.put(JSONObject().put("position", question.position).put("response", response)
                    .put("timeSpentMs", timeSpentMs[question.position]?.coerceAtMost(Int.MAX_VALUE.toLong())?.toInt() ?: 0))
                }
            }
            val result = JSONObject(request(baseUrl, "/quiz-attempts/${attempt.id}/submit", token, "POST",
                JSONObject().put("answers", rows)))
            val answerRows = result.optJSONArray("answers") ?: JSONArray()
            val explanations = (0 until answerRows.length()).associate { i ->
                val item = answerRows.getJSONObject(i)
                item.getInt("position") to (item.optBoolean("isCorrect") to item.optString("explanation"))
            }
            JiaQuizResult(result.getInt("questionCount"), result.getInt("correctCount"),
                result.getDouble("scorePercent"), explanations, result.optBoolean("timedOut", false))
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
    suspend fun lessonProgress(baseUrl: String, token: String): List<JiaLessonProgress> =
        withContext(Dispatchers.IO) {
            val rows = JSONArray(request(baseUrl, "/me/lesson-progress", token, "GET"))
            (0 until rows.length()).map { i ->
                val row = rows.getJSONObject(i)
                JiaLessonProgress(row.getString("lessonSlug"), row.getInt("contentRevision"),
                    row.getString("status"), row.getDouble("progressPercent"), row.getString("updatedAt"))
            }
        }

    suspend fun updateLessonProgress(
        baseUrl: String, token: String, lessonSlug: String, contentRevision: Int,
        progressPercent: Double, lastPosition: JSONObject? = null
    ) = withContext(Dispatchers.IO) {
        require(contentRevision > 0) { "contentRevision must be positive" }
        require(progressPercent in 0.0..100.0) { "progressPercent must be between 0 and 100" }
        val body = JSONObject().put("contentRevision", contentRevision)
            .put("progressPercent", progressPercent)
        if (lastPosition != null) body.put("lastPosition", lastPosition)
        request(baseUrl, "/me/lesson-progress/$lessonSlug", token, "PUT", body)
    }

    suspend fun bookmarks(baseUrl: String, token: String): List<JiaLessonBookmark> =
        withContext(Dispatchers.IO) {
            val rows = JSONArray(request(baseUrl, "/me/bookmarks", token, "GET"))
            (0 until rows.length()).map { i ->
                val row = rows.getJSONObject(i)
                JiaLessonBookmark(row.getString("lessonSlug"), row.getString("title"), row.getString("createdAt"))
            }
        }

    suspend fun setBookmarked(baseUrl: String, token: String, lessonSlug: String, bookmarked: Boolean) =
        withContext(Dispatchers.IO) {
            request(baseUrl, "/me/bookmarks/$lessonSlug", token, if (bookmarked) "PUT" else "DELETE")
        }

}
