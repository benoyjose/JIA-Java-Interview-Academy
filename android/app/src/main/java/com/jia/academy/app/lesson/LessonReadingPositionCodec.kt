package com.jia.academy.app.lesson

import org.json.JSONObject

/** Small versioned anchor payload shared with the learner progress API. */
internal object LessonReadingPositionCodec {
    private const val VERSION = 1

    fun encode(offsetPx: Int, contentRevision: Int): String {
        require(offsetPx >= 0)
        require(contentRevision > 0)
        return JSONObject()
            .put("version", VERSION)
            .put("scrollOffsetPx", offsetPx)
            .put("contentRevision", contentRevision)
            .toString()
    }

    fun decodeOffset(json: String?): Int? = runCatching {
        val value = JSONObject(json ?: return null)
        if (value.optInt("version") != VERSION) return null
        value.getInt("scrollOffsetPx").takeIf { it >= 0 }
    }.getOrNull()
}
