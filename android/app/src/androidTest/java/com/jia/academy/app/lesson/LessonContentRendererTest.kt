package com.jia.academy.app.lesson

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class LessonContentRendererTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun rendersRepresentativeLessonBlocksAndSafeFallbacks() {
        val raw = """{"schemaVersion":1,"blocks":[
          {"type":"overview","title":"Overview","body":"Spring Boot simplifies application setup."},
          {"type":"objectives","title":"Learning objectives","body":"Understand auto-configuration."},
          {"type":"list","title":"Prerequisites","items":["JDK 17","OOP"]},
          {"type":"interviewAnswer","title":"Interview answer","body":["Explain dependency injection."],"expandedExplanation":"The container manages object creation."},
          {"type":"code","title":"Java example","language":"java","javaVersion":"17","source":"public class Demo {}","explanation":"A minimal class."},
          {"type":"callout","title":"Remember","tone":"info","body":"Prefer constructor injection."},
          {"type":"diagram","title":"Request flow","language":"mermaid","source":"flowchart TD\nA[Client] --> B[API]"},
          {"type":"image","title":"Architecture image","url":"https://cdn.example.com/architecture.png","alt":"Three-tier architecture","caption":"Three-tier view"},
          {"type":"video","title":"Walkthrough","url":"https://cdn.example.com/demo.mp4","alt":"Video walkthrough"},
          {"type":"resourceLink","title":"Official docs","label":"Spring documentation","url":"https://spring.io"},
          {"type":"futureBlock","title":"Unknown"}
        ]}"""

        composeRule.setContent {
            MaterialTheme { LessonContentRenderer(raw) }
        }

        composeRule.onNodeWithText("Overview").assertExists()
        composeRule.onNodeWithText("Learning objectives").assertExists()
        composeRule.onNodeWithText("Prerequisites").assertExists()
        composeRule.onNodeWithText("Interview answer").assertExists()
        val answerTop = composeRule.onNodeWithText("Interview answer").fetchSemanticsNode().boundsInRoot.top
        val overviewTop = composeRule.onNodeWithText("Overview").fetchSemanticsNode().boundsInRoot.top
        assertTrue("Concise interview answer should appear before supporting lesson detail", answerTop < overviewTop)
        composeRule.onNodeWithText("Java example").assertExists()
        composeRule.onNodeWithText("Remember").assertExists()
        composeRule.onNodeWithText("Request flow").assertExists()
        composeRule.onNodeWithText("Three-tier view").assertExists()
        composeRule.onNodeWithText("Walkthrough").assertExists()
        composeRule.onNodeWithText("Spring documentation").assertExists()
        composeRule.onNodeWithText("1 unsupported content block(s) were skipped.").assertExists()
        composeRule.onNodeWithText("Copy code").assertExists()
        composeRule.onNodeWithText("Client → API").assertExists()
    }

    @Test
    fun detailedInterviewExplanationStartsCollapsedAndCanBeExpanded() {
        val raw = """{"schemaVersion":1,"blocks":[
          {"type":"interviewAnswer","key":"answer","title":"Concise answer","body":["Use constructor injection."],"expandedExplanation":"It makes dependencies explicit and supports immutability."}
        ]}"""

        composeRule.setContent {
            MaterialTheme { LessonContentRenderer(raw) }
        }

        composeRule.onNodeWithText("Use constructor injection.").assertExists()
        composeRule.onNodeWithText("It makes dependencies explicit and supports immutability.")
            .assertDoesNotExist()
        composeRule.onNodeWithText("Show detailed explanation").performClick()
        composeRule.onNodeWithText("It makes dependencies explicit and supports immutability.")
            .assertExists()
        composeRule.onNodeWithText("Hide detailed explanation").performClick()
        composeRule.onNodeWithText("It makes dependencies explicit and supports immutability.")
            .assertDoesNotExist()
    }
}
