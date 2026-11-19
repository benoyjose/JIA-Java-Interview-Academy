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

    @Test(expected = IllegalArgumentException::class)
    fun rejectsUnsupportedSchemaVersion() {
        LessonContentParser.parse("""{"schemaVersion":2,"blocks":[]}""")
    }
}
