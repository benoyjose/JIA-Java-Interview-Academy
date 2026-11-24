package com.jia.academy.app.assessment
import org.junit.Assert.*;import org.junit.Test
class PracticeSessionControllerTest{
 private val prompt=PracticePrompt("p","Question","Explanation",listOf("wrong","right"),1)
 @Test fun answerSubmitAndNextAreGuarded(){val c=PracticeSessionController(listOf(prompt));c.answer(1);c.submit();assertEquals(1,c.state.value.score);c.next();assertTrue(c.state.value.completed)}
 @Test fun cannotSubmitWithoutPrompt(){val c=PracticeSessionController(emptyList());c.submit();assertFalse(c.state.value.submitted)}
 @Test fun completedSessionCalculatesAccuracyAndIncludesUnansweredForReview(){
  val second=PracticePrompt("p2","Second","More detail",listOf("A","B"),0)
  val c=PracticeSessionController(listOf(prompt,second));c.answer(0);c.submit();c.next();c.submit();c.next()
  assertEquals(0,c.state.value.score);assertEquals(0,c.state.value.accuracyPercent);assertEquals(1,c.state.value.answeredCount)
  assertEquals(listOf("p","p2"),c.state.value.missedPrompts.map{it.id})
 }
 @Test fun mistakeReviewIsOnlyAvailableAfterCompletion(){
  val c=PracticeSessionController(listOf(prompt));c.showMistakeReview();assertFalse(c.state.value.reviewingMistakes)
  c.answer(0);c.submit();c.next();c.showMistakeReview();assertTrue(c.state.value.reviewingMistakes)
  c.returnToSummary();assertFalse(c.state.value.reviewingMistakes)
 }
}
