package com.jia.academy.app.lesson

import org.json.JSONArray
import org.json.JSONObject

sealed interface LessonBlock {
    val key: String
    val title: String?

    data class Markdown(override val key: String, override val title: String?, val body: String) : LessonBlock
    data class Code(
        override val key: String, override val title: String?, val language: String,
        val source: String, val javaVersion: String?, val explanation: String?
    ) : LessonBlock
    data class Callout(override val key: String, override val title: String?, val tone: String, val body: String) : LessonBlock
    data class InterviewAnswer(
        override val key: String, override val title: String?, val mode: String,
        val points: List<String>, val expandedExplanation: String?
    ) : LessonBlock
}

data class LessonDocument(val schemaVersion: Int, val blocks: List<LessonBlock>, val skippedBlockCount: Int)

object LessonContentParser {
    const val SUPPORTED_SCHEMA_VERSION = 1

    fun parse(raw: String): LessonDocument {
        val root = JSONObject(raw)
        val version = root.optInt("schemaVersion", -1)
        require(version == SUPPORTED_SCHEMA_VERSION) { "Unsupported lesson content schema version: $version" }
        val source = root.optJSONArray("blocks") ?: JSONArray()
        val blocks = mutableListOf<LessonBlock>()
        var skipped = 0
        for (index in 0 until source.length()) {
            val item = source.optJSONObject(index)
            val block = item?.let { parseBlock(it, index) }
            if (block == null) skipped++ else blocks += block
        }
        return LessonDocument(version, blocks, skipped)
    }

    private fun parseBlock(item: JSONObject, index: Int): LessonBlock? {
        val type = item.optString("type")
        val key = item.optString("key").ifBlank { "block-$index" }
        val title = item.optString("title").takeIf(String::isNotBlank)
        return when (type) {
            "markdown" -> item.optString("body").takeIf(String::isNotBlank)?.let { LessonBlock.Markdown(key, title, it) }
            "code" -> item.optString("source").takeIf(String::isNotBlank)?.let {
                LessonBlock.Code(key, title, item.optString("language", "text"), it,
                    item.optString("javaVersion").takeIf(String::isNotBlank),
                    item.optString("explanation").takeIf(String::isNotBlank))
            }
            "callout" -> item.optString("body").takeIf(String::isNotBlank)?.let {
                LessonBlock.Callout(key, title, item.optString("tone", "info"), it)
            }
            "interviewAnswer" -> {
                val points = item.optJSONArray("body")?.strings().orEmpty().filter(String::isNotBlank)
                if (points.isEmpty()) null else LessonBlock.InterviewAnswer(key, title,
                    item.optString("mode", "concise"), points,
                    item.optString("expandedExplanation").takeIf(String::isNotBlank))
            }
            else -> null
        }
    }

    private fun JSONArray.strings(): List<String> =
        (0 until length()).mapNotNull { index -> optString(index).takeIf(String::isNotBlank) }
}
