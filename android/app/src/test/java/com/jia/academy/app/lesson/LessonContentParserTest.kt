package com.jia.academy.app.lesson

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LessonContentParserTest {
    @Test fun parsesAllSupportedBlockTypes() {
        val document = LessonContentParser.parse(
            """{"schemaVersion":1,"blocks":[
              {"type":"markdown","key":"overview","title":"Overview","body":"Intro"},
              {"type":"code","key":"sample","language":"java","source":"System.out.println(1);","javaVersion":"17"},
              {"type":"callout","key":"warning","tone":"warning","body":"Avoid this"},
              {"type":"interviewAnswer","key":"answer","body":["Point one","Point two"]}
            ]}"""
        )
        assertEquals(4, document.blocks.size)
        assertEquals(0, document.skippedBlockCount)
        assertTrue(document.blocks[0] is LessonBlock.Markdown)
        assertTrue(document.blocks[1] is LessonBlock.Code)
        assertTrue(document.blocks[2] is LessonBlock.Callout)
        assertTrue(document.blocks[3] is LessonBlock.InterviewAnswer)
    }

    @Test fun skipsUnknownAndMalformedBlocksWithoutRejectingDocument() {
        val document = LessonContentParser.parse(
            """{"schemaVersion":1,"blocks":[
              {"type":"futureBlock","key":"future"},
              {"type":"code","key":"empty","source":""},
              {"type":"markdown","key":"ok","body":"Readable"}
            ]}"""
        )
        assertEquals(1, document.blocks.size)
        assertEquals(2, document.skippedBlockCount)
    }

    @Test fun parsesCurriculumSectionsListsDiagramsAndSafeResourceLinks() {
        val document = LessonContentParser.parse(
            """{"schemaVersion":1,"blocks":[
              {"type":"overview","title":"Overview","body":"Intro"},
              {"type":"objectives","body":"Learn the topic"},
              {"type":"list","title":"Prerequisites","items":["Java basics","OOP"]},
              {"type":"diagram","language":"mermaid","source":"A-->B"},
              {"type":"resourceLink","label":"Official docs","url":"https://docs.oracle.com"},
              {"type":"resourceLink","label":"Unsafe","url":"javascript:alert(1)"}
            ]}"""
        )
        assertEquals(5, document.blocks.size)
        assertEquals(1, document.skippedBlockCount)
        assertTrue(document.blocks[0] is LessonBlock.Section)
        assertTrue(document.blocks[2] is LessonBlock.BulletList)
        assertTrue(document.blocks[3] is LessonBlock.Diagram)
        assertTrue(document.blocks[4] is LessonBlock.ResourceLink)
    }

    @Test fun parsesAccessibleHttpsMediaAndRejectsUnsafeOrUnlabelledMedia() {
        val document = LessonContentParser.parse(
            """{"schemaVersion":1,"blocks":[
              {"type":"image","url":"https://cdn.example.com/architecture.png","alt":"Request flow diagram","caption":"Service interaction"},
              {"type":"video","url":"https://cdn.example.com/demo.mp4","alt":"Spring transaction walkthrough"},
              {"type":"image","url":"http://cdn.example.com/insecure.png","alt":"Insecure image"},
              {"type":"image","url":"https://cdn.example.com/no-alt.png","alt":""}
            ]}"""
        )
        assertEquals(2, document.blocks.size)
        assertEquals(2, document.skippedBlockCount)
        assertTrue(document.blocks.all { it is LessonBlock.Media })
    }

    @Test fun parsesMermaidFlowchartNodesAndLabeledEdges() {
        val diagram = MermaidDiagramParser.parse(
            """flowchart TD
              A[Client] -->|HTTP| B[API]
              B --> C[Database]
            """
        )
        assertEquals(listOf("Client", "API", "Database"), diagram.nodes.map { it.label })
        assertEquals(2, diagram.edges.size)
        assertEquals("A", diagram.edges[0].from)
        assertEquals("B", diagram.edges[0].to)
        assertEquals("HTTP", diagram.edges[0].label)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsupportedSchemaVersion() {
        LessonContentParser.parse("""{"schemaVersion":2,"blocks":[]}""")
    }

    @Test fun rendersBoldItalicAndInlineCodeWithoutMarkdownMarkers() {
        val rendered = markdownToAnnotatedString("Use **immutable** values, *avoid* mutation and call `copy()`.")
        assertEquals("Use immutable values, avoid mutation and call copy().", rendered.text)
        assertTrue(rendered.spanStyles.any { it.item.fontWeight == androidx.compose.ui.text.font.FontWeight.Bold })
        assertTrue(rendered.spanStyles.any { it.item.fontStyle == androidx.compose.ui.text.font.FontStyle.Italic })
        assertTrue(rendered.spanStyles.any { it.item.fontFamily == androidx.compose.ui.text.font.FontFamily.Monospace })
    }

    @Test fun leavesUnmatchedMarkdownDelimitersAsPlainText() {
        val rendered = markdownToAnnotatedString("Keep **unfinished and `unclosed")
        assertEquals("Keep **unfinished and `unclosed", rendered.text)
        assertTrue(rendered.spanStyles.isEmpty())
    }
}
