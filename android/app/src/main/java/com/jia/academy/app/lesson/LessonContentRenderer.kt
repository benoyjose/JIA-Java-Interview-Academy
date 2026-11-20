package com.jia.academy.app.lesson

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import coil.compose.AsyncImage
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

@Composable
fun LessonContentRenderer(raw: String) {
    val clipboard = LocalClipboardManager.current
    val document = runCatching { LessonContentParser.parse(raw) }.getOrNull()
    if (document == null) {
        Text("This lesson uses an unsupported or invalid content format.")
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        document.blocks.forEach { block ->
            when (block) {
                is LessonBlock.Markdown -> LessonTextBlock(block.title, block.body)
                is LessonBlock.Section -> LessonTextBlock(block.title, block.body)
                is LessonBlock.BulletList -> Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(block.title ?: "Key points", style = MaterialTheme.typography.titleMedium)
                        block.items.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
                    }
                }
                is LessonBlock.Diagram -> LessonDiagramRenderer(block)
                is LessonBlock.Media -> {
                    val uriHandler = LocalUriHandler.current
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(block.title ?: if (block.kind == "image") "Illustration" else "Video",
                                style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                            if (block.kind == "image") {
                                AsyncImage(model = block.url, contentDescription = block.altText,
                                    modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp))
                            } else {
                                Text(block.altText, style = MaterialTheme.typography.bodyMedium)
                                TextButton(onClick = { uriHandler.openUri(block.url) }) { Text("Open video") }
                            }
                            block.caption?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                        }
                    }
                }
                is LessonBlock.ResourceLink -> {
                    val uriHandler = LocalUriHandler.current
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(block.title ?: "Further reading", style = MaterialTheme.typography.titleSmall,
                                modifier = Modifier.semantics { heading() })
                            Text(block.label, style = MaterialTheme.typography.bodyMedium)
                            TextButton(onClick = { uriHandler.openUri(block.url) }) {
                                Text("Open resource")
                            }
                        }
                    }
                }
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
                            SelectionContainer {
                                Text(highlightCode(block.source), Modifier.horizontalScroll(rememberScrollState()),
                                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
                            }
                            TextButton(onClick = { clipboard.setText(AnnotatedString(block.source)) }) {
                                Text("Copy code")
                            }
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
                is LessonBlock.InterviewAnswer -> InterviewAnswerCard(block)
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
private fun highlightCode(source: String): AnnotatedString {
    val keywordColor = MaterialTheme.colorScheme.primary
    val literalColor = MaterialTheme.colorScheme.secondary
    val commentColor = MaterialTheme.colorScheme.tertiary
    val tokenPattern = remember {
        Regex("""//[^\\n]*|/\\*[\\s\\S]*?\\*/|"(?:\\\\.|[^"\\\\])*"|'(?:\\\\.|[^'\\\\])*'|\\b\\d+(?:\\.\\d+)?\\b|\\b(?:public|private|protected|static|final|class|interface|enum|record|void|new|return|if|else|for|while|try|catch|throw|throws|extends|implements|import|package|var|val|fun|suspend|override|data|object|when|true|false|null|this|super|static|const|async|await)\\b""")
    }
    return buildAnnotatedString {
        var cursor = 0
        tokenPattern.findAll(source).forEach { match ->
            append(source.substring(cursor, match.range.first))
            val token = match.value
            val color = when {
                token.startsWith("//") || token.startsWith("/*") -> commentColor
                token.startsWith("\"") || token.startsWith("'") || token.firstOrNull()?.isDigit() == true -> literalColor
                else -> keywordColor
            }
            withStyle(SpanStyle(color = color, fontWeight = FontWeight.SemiBold)) { append(token) }
            cursor = match.range.last + 1
        }
        append(source.substring(cursor))
    }
}

@Composable
private fun LessonTextBlock(title: String?, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        title?.let { Text(it, style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() }) }
        Text(markdownToAnnotatedString(body), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun InterviewAnswerCard(block: LessonBlock.InterviewAnswer) {
    var expanded by remember(block.key) { mutableStateOf(false) }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(block.title ?: "Interview answer", style = MaterialTheme.typography.titleMedium)
            Text(block.points.mapIndexed { index, point -> "${index + 1}. $point" }.joinToString("\n"),
            modifier = Modifier.semantics { contentDescription = "Interview answer with ${block.points.size} key points" })
            block.expandedExplanation?.let {
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Hide detailed explanation" else "Show detailed explanation")
                }
                if (expanded) Text(it, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun LessonDiagramRenderer(block: LessonBlock.Diagram) {
    val nodePattern = remember { Regex("""([A-Za-z0-9_]+)\s*[\[\(]([^\]\)]+)[\]\)]""") }
    val nodes = remember(block.source) {
        nodePattern.findAll(block.source).associate { it.groupValues[1] to it.groupValues[2].trim() }.toList()
    }
    val description = nodes.joinToString(", ") { it.second }.ifBlank { block.source.take(300) }
    Card(Modifier.fillMaxWidth().semantics { contentDescription = "Flow diagram: " + description }) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(block.title ?: "Diagram", style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { heading() })
            if (nodes.isEmpty()) {
                Text("Diagram source (" + block.language + ")", style = MaterialTheme.typography.labelSmall)
                Text(block.source, Modifier.horizontalScroll(rememberScrollState()),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace))
            } else {
                nodes.forEachIndexed { index, node ->
                    Card(Modifier.fillMaxWidth()) {
                        Text(node.second, Modifier.fillMaxWidth().padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium)
                    }
                    if (index < nodes.lastIndex) {
                        Text("↓", Modifier.fillMaxWidth().semantics { contentDescription = "Leads to next step" },
                            style = MaterialTheme.typography.titleLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                    }
                }
            }
        }
    }
}
