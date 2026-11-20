package com.jia.academy.app.catalogue

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class Technology(val id: String, val slug: String, val name: String, val description: String?)
data class Category(val id: String, val slug: String, val name: String, val technologySlug: String)
data class LessonSummary(val id: String, val slug: String, val title: String, val summary: String?, val difficulty: String)
data class QuizAvailability(val difficulty: String, val availableQuestionCount: Int)

data class LessonDetail(
    val id: String,
    val slug: String,
    val title: String,
    val summary: String?,
    val difficulty: String,
    val sourceType: String,
    val contentJson: String?
)

class CatalogueRepository {
    private fun apiUrl(baseUrl: String, path: String): String {
        require(baseUrl.isNotBlank()) {
            "Configure JIA_API_BASE_URL in ~/.gradle/gradle.properties to connect to the JIA API."
        }
        return baseUrl.trimEnd('/') + "/api/v1" + path
    }

    private suspend fun get(baseUrl: String, path: String): String = withContext(Dispatchers.IO) {
        val connection = (URL(apiUrl(baseUrl, path)).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
        }
        try {
            val status = connection.responseCode
            if (status !in 200..299) error("JIA API returned HTTP $status")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    private fun segment(value: String) = URLEncoder.encode(value, Charsets.UTF_8.name())

    suspend fun getTechnologies(baseUrl: String): List<Technology> {
        val json = JSONArray(get(baseUrl, "/technologies"))
        return (0 until json.length()).map { i ->
            val item = json.getJSONObject(i)
            Technology(item.getString("id"), item.getString("slug"), item.getString("name"),
                item.optString("description").takeIf(String::isNotBlank))
        }
    }

    suspend fun getCategories(baseUrl: String, technologySlug: String): List<Category> {
        val json = JSONArray(get(baseUrl, "/technologies/${segment(technologySlug)}/categories"))
        return (0 until json.length()).map { i ->
            val item = json.getJSONObject(i)
            Category(item.getString("id"), item.getString("slug"), item.getString("name"),
                item.optString("technologySlug", technologySlug))
        }
    }

    suspend fun getLessons(baseUrl: String, categorySlug: String): List<LessonSummary> {
        val json = JSONArray(get(baseUrl, "/categories/${segment(categorySlug)}/lessons"))
        return (0 until json.length()).map { i ->
            val item = json.getJSONObject(i)
            LessonSummary(item.getString("id"), item.getString("slug"), item.getString("title"),
                item.optString("summary").takeIf(String::isNotBlank),
                item.optString("difficulty", "Not specified"))
        }
    }

    suspend fun getQuizAvailability(baseUrl: String, difficulty: String): QuizAvailability {
        val query = URLEncoder.encode(difficulty, Charsets.UTF_8.name())
        val item = JSONObject(get(baseUrl, "/quizzes/availability?difficulty=$query"))
        return QuizAvailability(item.getString("difficulty"), item.getInt("availableQuestionCount"))
    }

    suspend fun getLesson(baseUrl: String, slug: String): LessonDetail {
        val item = JSONObject(get(baseUrl, "/lessons/${segment(slug)}"))
        return LessonDetail(item.getString("id"), item.getString("slug"), item.getString("title"),
            item.optString("summary").takeIf(String::isNotBlank),
            item.optString("difficulty", "Not specified"),
            item.optString("sourceType", "Unknown"),
            item.optString("contentJson").takeIf(String::isNotBlank))
    }
}
