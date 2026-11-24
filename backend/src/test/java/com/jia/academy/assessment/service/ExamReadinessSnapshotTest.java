package com.jia.academy.assessment.service;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ExamReadinessSnapshotTest {
    private final ExamReadinessSnapshot service = new ExamReadinessSnapshot();

    @Test void ignoresIncompleteAttemptsAndWeightsTimedPractice() {
        var result = service.calculate(List.of(
                new ExamReadinessSnapshot.Attempt(80, false, true),
                new ExamReadinessSnapshot.Attempt(100, true, true),
                new ExamReadinessSnapshot.Attempt(0, true, false)));
        assertEquals(91, result.readinessPercent());
        assertEquals(2, result.completedAttempts());
        assertEquals(1, result.timedAttempts());
        assertFalse(result.sampleSufficient());
    }

    @Test void emptyHistoryIsNotMisrepresentedAsReady() {
        assertEquals("NOT_ENOUGH_DATA", service.calculate(List.of()).band());
    }

    @Test void rejectsOutOfRangeScore() {
        assertThrows(IllegalArgumentException.class,
                () -> new ExamReadinessSnapshot.Attempt(101, false, true));
    }
}
