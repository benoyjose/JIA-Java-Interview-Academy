package com.jia.academy.app.assessment
import org.junit.Assert.*;import org.junit.Test
class PracticeSessionControllerTest{
 private val prompt=PracticePrompt("p","Question","Explanation",listOf("wrong","right"),1)
 @Test fun answerSubmitAndNextAreGuarded(){val c=PracticeSessionController(listOf(prompt));c.answer(1);c.submit();assertEquals(1,c.state.value.score);c.next();assertTrue(c.state.value.completed)}
 @Test fun cannotSubmitWithoutPrompt(){val c=PracticeSessionController(emptyList());c.submit();assertFalse(c.state.value.submitted)}
}
