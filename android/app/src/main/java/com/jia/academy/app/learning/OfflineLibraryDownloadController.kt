package com.jia.academy.app.learning

import com.jia.academy.app.catalogue.LessonDetail
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

/**
 * App-facing release downloader. The server filters the manifest by the current session's
 * entitlement and independently rechecks access for every lesson body.
 */
internal class OfflineLibraryDownloadController(
    private val baseUrl: String,
    private val store: OfflineLearningStore,
    private val accessToken: () -> String?,
    private val transport: ProgressSyncHttpTransport = UrlConnectionProgressSyncTransport()
) {
    suspend fun downloadLatest(onProgress: (OfflineDownloadProgress) -> Unit = {}): OfflineDownloadSummary {
        val apiBase = baseUrl.trimEnd('/') + "/api/v1"
        val token = accessToken()?.takeIf(String::isNotBlank)
        val manifestResponse = withContext(Dispatchers.IO) {
            transport.execute("$apiBase/content/releases/latest", "GET", token, null)
        }
        when {
            manifestResponse.status == 404 ->
                throw IllegalStateException("No published offline content release is available")
            manifestResponse.status == 401 || manifestResponse.status == 403 ->
                throw IllegalStateException("Sign in again to refresh your offline library")
            manifestResponse.status == 408 || manifestResponse.status == 429 || manifestResponse.status >= 500 ->
                throw IOException("Offline release lookup failed temporarily (HTTP ${manifestResponse.status})")
            manifestResponse.status !in 200..299 ->
                throw IllegalStateException("Offline release lookup was rejected (HTTP ${manifestResponse.status})")
        }
        val manifest = OfflineReleaseManifest.parse(JSONObject(manifestResponse.body))
        val coordinator = OfflineManifestDownloadCoordinator(store) { slug ->
            val encodedSlug = URLEncoder.encode(slug, Charsets.UTF_8.name())
            val response = withContext(Dispatchers.IO) {
                transport.execute("$apiBase/content/releases/${manifest.version}/lessons/$encodedSlug",
                    "GET", token, null)
            }
            when {
                response.status == 401 || response.status == 403 ->
                    throw IllegalStateException("Your sign-in or premium access is no longer valid for this lesson")
                response.status == 408 || response.status == 429 || response.status >= 500 ->
                    throw IOException("Lesson download failed temporarily (HTTP ${response.status})")
                response.status !in 200..299 ->
                    throw IllegalStateException("Lesson download was rejected (HTTP ${response.status})")
            }
            val json = JSONObject(response.body)
            LessonDetail(
                json.getString("id"), json.getString("slug"), json.getString("title"),
                json.optString("summary").takeIf(String::isNotBlank),
                json.optString("difficulty", "Not specified"), json.optInt("contentRevision", 1),
                json.optString("sourceType", "Unknown"),
                json.optString("contentJson").takeIf(String::isNotBlank)
            )
        }
        return withContext(Dispatchers.IO) { coordinator.download(manifest, onProgress) }
    }
}
