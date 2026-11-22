package com.jia.academy.app.learning

import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

internal data class SyncHttpResponse(val status: Int, val body: String)

internal fun interface ProgressSyncHttpTransport {
    fun execute(url: String, method: String, token: String?, body: String?): SyncHttpResponse
}

internal class UrlConnectionProgressSyncTransport : ProgressSyncHttpTransport {
    override fun execute(url: String, method: String, token: String?, body: String?): SyncHttpResponse {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 10_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            if (token != null) setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }
        return try {
            if (body != null) connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            SyncHttpResponse(status, stream?.bufferedReader()?.use { it.readText() }.orEmpty())
        } finally {
            connection.disconnect()
        }
    }
}

internal enum class SyncResponseAction { ACKNOWLEDGE, REBASE, RETRY, REJECT }

internal object ProgressSyncResponsePolicy {
    fun classify(status: Int): SyncResponseAction = when {
        status in 200..299 -> SyncResponseAction.ACKNOWLEDGE
        status == 409 -> SyncResponseAction.REBASE
        status == 408 || status == 429 || status >= 500 -> SyncResponseAction.RETRY
        else -> SyncResponseAction.REJECT
    }

    fun requestBody(item: PendingLessonProgress): JSONObject =
        JSONObject().put("contentRevision", item.contentRevision)
            .put("progressPercent", item.progressPercent)
            .also { json -> item.lastPositionJson?.let { json.put("lastPosition", JSONObject(it)) } }
}
