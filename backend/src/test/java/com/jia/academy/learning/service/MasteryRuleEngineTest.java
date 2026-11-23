package com.jia.academy.learning.service;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class MasteryRuleEngineTest {
    private final MasteryRuleEngine engine = new MasteryRuleEngine();
    private final Instant now = Instant.parse("2026-11-23T00:00:00Z");

    @Test void lessonViewsOrCompletionWithoutAssessmentCannotClaimMastery() {
        var result = engine.evaluate(new MasteryRuleEngine.Evidence(10, 10, 0, 0, 100, now), now);
        assertEquals(MasteryRuleEngine.Band.NOT_ASSESSED, result.band());
        assertEquals(0, result.score());
    }

    @Test void objectiveEvidenceDominatesAndStrongRecentEvidenceCanMaster() {
        var result = engine.evaluate(new MasteryRuleEngine.Evidence(8, 10, 19, 20, 80, now), now);
        assertEquals(85, result.score());
        assertEquals(MasteryRuleEngine.Band.MASTERED, result.band());
        assertFalse(result.reviewDue());
    }

    @Test void masteryBecomesReviewDueAfterThirtyDays() {
        var result = engine.evaluate(new MasteryRuleEngine.Evidence(10, 10, 9, 10, 90,
                now.minusSeconds(30L * 24 * 60 * 60)), now);
        assertEquals(MasteryRuleEngine.Band.REVIEW_DUE, result.band());
        assertTrue(result.reviewDue());
    }

    @Test void rejectsImpossibleEvidence() {
        assertThrows(IllegalArgumentException.class,
                () -> new MasteryRuleEngine.Evidence(3, 2, 0, 0, 50, now));
        assertThrows(IllegalArgumentException.class,
                () -> new MasteryRuleEngine.Evidence(0, 1, 4, 3, 50, now));
    }
}
