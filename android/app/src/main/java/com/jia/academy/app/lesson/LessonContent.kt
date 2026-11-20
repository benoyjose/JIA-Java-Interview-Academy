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
    data class Section(override val key: String, override val title: String?, val body: String) : LessonBlock
    data class BulletList(override val key: String, override val title: String?, val items: List<String>) : LessonBlock
    data class Diagram(override val key: String, override val title: String?, val language: String, val source: String) : LessonBlock
    data class ResourceLink(override val key: String, override val title: String?, val label: String, val url: String) : LessonBlock
    data class Media(override val key: String, override val title: String?, val kind: String, val url: String, val altText: String, val caption: String?) : LessonBlock
    data class InterviewAnswer(
        override val key: String, override val title: String?, val mode: String,
        val points: List<String>, val expandedExplanation: String?
    ) : LessonBlock
}

data class LessonDocument(val schemaVersion: Int, val blocks: List<LessonBlock>, val skippedBlockCount: Int)

data class DiagramNode(val id: String, val label: String)
data class DiagramEdge(val from: String, val to: String, val label: String?)
data class MermaidDiagram(val nodes: List<DiagramNode>, val edges: List<DiagramEdge>)

/** Parses the common Mermaid flowchart subset into an accessible node/edge model. */
object MermaidDiagramParser {
    private val nodePattern = Regex("""([A-Za-z0-9_]+)\\s*(?:\\[([^\\]]+)\\]|\\(([^)]+)\\)|\\{([^}]+)\\})?""")
    private val edgePattern = Regex("""([A-Za-z0-9_]+)\\s*(?:-->|---|-.->|==>)\\s*(?:\\|([^|]+)\\|\\s*)?([A-Za-z0-9_]+)""")

    fun parse(source: String): MermaidDiagram {
        val nodes = linkedMapOf<String, String>()
        val edges = mutableListOf<DiagramEdge>()
        source.lineSequence()
            .map(String::trim)
            .filter { it.isNotBlank() && !it.startsWith("%%")
                    && !it.startsWith("graph ") && !it.startsWith("flowchart ") }
            .forEach { line ->
                edgePattern.findAll(line).forEach { match ->
                    val from = match.groupValues[1]
                    val to = match.groupValues[3]
                    nodes.putIfAbsent(from, from)
                    nodes.putIfAbsent(to, to)
                    edges += DiagramEdge(from, to, match.groupValues[2].trim().takeIf(String::isNotBlank))
                }
                nodePattern.findAll(line).forEach { match ->
                    val id = match.groupValues[1]
                    val label = listOf(match.groupValues[2], match.groupValues[3], match.groupValues[4])
                        .firstOrNull(String::isNotBlank) ?: id
                    nodes[id] = label.trim()
                }
            }
        return MermaidDiagram(nodes.map { DiagramNode(it.key, it.value) }, edges.distinct())
    }
}

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
            "section", "overview", "objectives", "prerequisites", "explanation",
            "tradeoffs", "commonMistakes", "troubleshooting", "followUps" ->
                item.optString("body").takeIf(String::isNotBlank)?.let { LessonBlock.Section(key, title, it) }
            "list" -> item.optJSONArray("items")?.strings()?.filter(String::isNotBlank)
                ?.takeIf { it.isNotEmpty() }?.let { LessonBlock.BulletList(key, title, it) }
            "diagram" -> item.optString("source").takeIf(String::isNotBlank)?.let {
                LessonBlock.Diagram(key, title, item.optString("language", "mermaid"), it)
            }
            "image", "video" -> {
                val url = item.optString("url")
                val alt = item.optString("alt").trim()
                val scheme = runCatching { java.net.URI(url).scheme?.lowercase() }.getOrNull()
                if (scheme != "https" || alt.isBlank() || url.length > 2048) null
                else LessonBlock.Media(key, title, type, url, alt,
                    item.optString("caption").takeIf(String::isNotBlank))
            }
            "resourceLink" -> {
                val url = item.optString("url")
                val scheme = runCatching { java.net.URI(url).scheme?.lowercase() }.getOrNull()
                if (scheme != "https" && scheme != "http") null
                else LessonBlock.ResourceLink(key, title, item.optString("label").ifBlank { url }, url)
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
