package com.jia.academy.app.assessment

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JiaQuizQuestionTypeTest {
    @Test
    fun objectiveQuestionsRemainSelectable() {
        listOf("SINGLE_SELECT", "MULTI_SELECT", "TRUE_FALSE").forEach {
            assertFalse(it.requiresFreeResponse())
        }
    }

    @Test
    fun outputDebugCodingSqlAndScenarioUseTextResponses() {
        listOf("OUTPUT_PREDICTION", "DEBUGGING", "CODING", "SQL", "SCENARIO").forEach {
            assertTrue("$it should use a text response", it.requiresFreeResponse())
        }
    }

    @Test
    fun unknownQuestionTypeDoesNotSilentlyBecomeFreeText() {
        assertFalse("UNKNOWN".requiresFreeResponse())
    }
}
