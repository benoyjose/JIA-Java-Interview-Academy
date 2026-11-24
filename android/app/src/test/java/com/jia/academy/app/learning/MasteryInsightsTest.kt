package com.jia.academy.app.learning
import org.junit.Assert.assertEquals
import org.junit.Test
class MasteryInsightsTest {
 @Test fun noEvidenceDoesNotClaimMastery(){ val t=TopicMastery("jvm","JVM",0,0,0); assertEquals("Not assessed",t.band); assertEquals(0,t.accuracy) }
 @Test fun scoreBandsAreStable(){ assertEquals("Developing",TopicMastery("a","A",10,5,2).band); assertEquals("Practicing",TopicMastery("b","B",10,7,2).band); assertEquals("Mastered",TopicMastery("c","C",10,9,2).band) }
}