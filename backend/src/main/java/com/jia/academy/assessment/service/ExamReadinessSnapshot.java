package com.jia.academy.assessment.service;

import java.util.List;

/** Pure, deterministic readiness summary for a learner's recent practice. */
public final class ExamReadinessSnapshot {
    public record Attempt(int scorePercent, boolean timed, boolean completed) {
        public Attempt {
            if (scorePercent < 0 || scorePercent > 100) {
                throw new IllegalArgumentException("scorePercent must be 0..100");
            }
        }
    }

    public record Result(int readinessPercent, int completedAttempts, int timedAttempts,
                         String band, boolean sampleSufficient) {}

    public Result calculate(List<Attempt> attempts) {
        if (attempts == null) throw new IllegalArgumentException("attempts are required");
        List<Attempt> completed = attempts.stream().filter(Attempt::completed).toList();
        if (completed.isEmpty()) return new Result(0, 0, 0, "NOT_ENOUGH_DATA", false);

        double weightedScore = completed.stream()
                .mapToDouble(a -> a.scorePercent() * (a.timed() ? 1.15 : 1.0)).sum();
        double weights = completed.stream().mapToDouble(a -> a.timed() ? 1.15 : 1.0).sum();
        int readiness = (int) Math.round(weightedScore / weights);
        int timed = (int) completed.stream().filter(Attempt::timed).count();
        String band = readiness >= 85 ? "READY" : readiness >= 65 ? "NEAR_READY" : "NEEDS_PRACTICE";
        return new Result(readiness, completed.size(), timed, band, completed.size() >= 3);
    }
}
