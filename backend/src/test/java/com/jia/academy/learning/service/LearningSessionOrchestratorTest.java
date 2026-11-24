package com.jia.academy.learning.service;
import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;import java.util.List;import org.junit.jupiter.api.Test;
class LearningSessionOrchestratorTest {
 @Test void prioritizesWeaknessAndCapsSession(){var service=new LearningSessionOrchestrator(new AdaptiveReviewScheduler());var plan=service.compose(List.of(new LearningSessionOrchestrator.Topic("a","A",2,30),new LearningSessionOrchestrator.Topic("b","B",9,20)),List.of(),Instant.parse("2026-01-01T00:00:00Z"),25,20);assertEquals(20,plan.budgetMinutes());assertEquals("b",plan.items().get(0).reference());assertTrue(plan.scheduledMinutes()<=20);}
 @Test void rejectsOutOfRangeGoal(){var service=new LearningSessionOrchestrator(new AdaptiveReviewScheduler());assertThrows(IllegalArgumentException.class,()->service.compose(List.of(),List.of(),Instant.now(),20,0));}
}
