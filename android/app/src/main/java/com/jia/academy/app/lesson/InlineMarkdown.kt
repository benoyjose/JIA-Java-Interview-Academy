package com.jia.academy.app.lesson

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight

private val inlineMarkdown = Regex("""\*\*([^*\n]+)\*\*|(?<!\*)\*([^*\n]+)\*(?!\*)|`([^`\n]+)`""")

/** Renders the small, safe inline-Markdown subset used in authored lesson bodies. */
internal fun markdownToAnnotatedString(source: String): AnnotatedString = buildAnnotatedString {
    var cursor = 0
    inlineMarkdown.findAll(source).forEach { match ->
        append(source.substring(cursor, match.range.first))
        val content = match.groups[1]?.value ?: match.groups[2]?.value ?: match.groups[3]?.value.orEmpty()
        val start = length
        append(content)
        val style = when {
            match.groups[1] != null -> SpanStyle(fontWeight = FontWeight.Bold)
            match.groups[2] != null -> SpanStyle(fontStyle = FontStyle.Italic)
            else -> SpanStyle(fontFamily = FontFamily.Monospace)
        }
        addStyle(style, start, length)
        cursor = match.range.last + 1
    }
    append(source.substring(cursor))
}
