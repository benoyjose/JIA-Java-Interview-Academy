package com.jia.academy.app.assessment
import org.junit.Assert.*;import org.junit.Test;import java.time.LocalDate
class SpacedReviewSessionTest{
 private val cards=listOf(ReviewCard("streams","Streams","Q","A"),ReviewCard("threads","Threads","Q2","A2"))
 @Test fun revealRequiredAndEachCardAdvancesOnce(){val s=SpacedReviewSession(cards);try{s.grade(4);fail("reveal required")}catch(_:IllegalStateException){};assertTrue(s.reveal());assertEquals(1,s.grade(5).completed);s.reveal();assertTrue(s.grade(3).finished);try{s.grade(4);fail("completed")}catch(_:IllegalStateException){}}
 @Test fun ratingsAreBoundedAndReviewDelayIsPredictable(){val s=SpacedReviewSession(cards);assertEquals(LocalDate.of(2026,11,25),s.nextDue(2,LocalDate.of(2026,11,24)));try{s.nextDue(9,LocalDate.now());fail("invalid")}catch(_:IllegalArgumentException){}}
}
