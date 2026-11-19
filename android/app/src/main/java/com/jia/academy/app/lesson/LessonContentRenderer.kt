package com.jia.academy.app.lesson

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

@Composable
fun LessonContentRenderer(raw: String) {
    val document = runCatching { LessonContentParser.parse(raw) }.getOrNull()
    if (document == null) {
        Text("This lesson uses an unsupported or invalid content format.")
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        document.blocks.forEach { block ->
            when (block) {
                is LessonBlock.Markdown -> LessonTextBlock(block.title, block.body)
                is LessonBlock.Code -> {
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(block.title ?: "Code example", style = MaterialTheme.typography.titleMedium)
                            Text(
                                buildString {
                                    append(block.language.uppercase())
                                    block.javaVersion?.let { append(" · Java ").append(it) }
                                },
                                style = MaterialTheme.typography.labelSmall
                            )
                            Text(block.source, Modifier.horizontalScroll(rememberScrollState()),
                                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
                            block.explanation?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                        }
                    }
                }
                is LessonBlock.Callout -> Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(block.title ?: block.tone.replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.titleSmall)
                        Text(block.body, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                is LessonBlock.InterviewAnswer -> Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(block.title ?: "Interview answer", style = MaterialTheme.typography.titleMedium)
                        Text(block.points.mapIndexed { index, point -> "${index + 1}. $point" }.joinToString("\n"))
                        block.expandedExplanation?.let {
                            Text("Expanded explanation", style = MaterialTheme.typography.titleSmall)
                            Text(it, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
        if (document.skippedBlockCount > 0) {
            Text("${document.skippedBlockCount} unsupported content block(s) were skipped.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (document.blocks.isEmpty()) Text("No readable lesson sections are available yet.")
    }
}

@Composable
private fun LessonTextBlock(title: String?, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        title?.let { Text(it, style = MaterialTheme.typography.titleMedium) }
        Text(body, style = MaterialTheme.typography.bodyLarge)
    }
}
