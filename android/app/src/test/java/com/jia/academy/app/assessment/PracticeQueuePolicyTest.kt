package com.jia.academy.app.assessment
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
class PracticeQueuePolicyTest {
 private val now=Instant.parse("2026-11-24T00:00:00Z")
 @Test fun missedEvidenceIsDueAndRanksFirst(){val q=PracticeQueuePolicy.due(listOf(PracticeEvidence("solid",now.minusSeconds(864000),5,0,95),PracticeEvidence("weak",now.minusSeconds(259200),1,2,30)),now);assertEquals("weak",q.first().id);assertEquals(1,q.first().intervalDays)}
 @Test fun futureAndMissingItemsAreExcluded(){assertTrue(PracticeQueuePolicy.due(listOf(PracticeEvidence("future",now,5,0,95),PracticeEvidence("unknown",null,0,0,0)),now).isEmpty())}
 @Test(expected=IllegalArgumentException::class) fun limitIsBounded(){PracticeQueuePolicy.due(emptyList(),now,101)}
}
