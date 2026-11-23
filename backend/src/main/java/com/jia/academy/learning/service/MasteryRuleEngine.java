package com.jia.academy.learning.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Transparent, deterministic mastery estimate. Lesson opens/views are deliberately
 * excluded: completion is weak evidence and objective assessment is strongest.
 * The result is a revisioned decision aid, not an opaque ranking model.
 */
public final class MasteryRuleEngine {
    public static final int MODEL_VERSION = 1;

    public enum Band { NOT_ASSESSED, DEVELOPING, PRACTICING, MASTERED, REVIEW_DUE }

    public record Evidence(
            int completedLessons,
            int availableLessons,
            int correctObjectiveAnswers,
            int objectiveAnswers,
            int confidencePercent,
            Instant lastDemonstratedAt) {
        public Evidence {
            if (completedLessons < 0 || availableLessons < 0 || completedLessons > availableLessons) {
                throw new IllegalArgumentException("Lesson completion counts are invalid");
            }
            if (correctObjectiveAnswers < 0 || objectiveAnswers < 0
                    || correctObjectiveAnswers > objectiveAnswers) {
                throw new IllegalArgumentException("Objective assessment counts are invalid");
            }
            if (confidencePercent < 0 || confidencePercent > 100) {
                throw new IllegalArgumentException("Confidence must be between 0 and 100");
            }
        }
    }

    public record Result(int modelVersion, int score, Band band, boolean reviewDue) {}

    public Result evaluate(Evidence evidence, Instant now) {
        Objects.requireNonNull(evidence, "evidence");
        Objects.requireNonNull(now, "now");
        if (evidence.objectiveAnswers() == 0) {
            return new Result(MODEL_VERSION, 0, Band.NOT_ASSESSED, false);
        }

        double quiz = 100.0 * evidence.correctObjectiveAnswers() / evidence.objectiveAnswers();
        double completion = evidence.availableLessons() == 0 ? 0.0
                : 100.0 * evidence.completedLessons() / evidence.availableLessons();
        // 75% objective evidence, 15% completion, 10% learner confidence.
        int score = (int) Math.round(quiz * 0.75 + completion * 0.15
                + evidence.confidencePercent() * 0.10);
        boolean reviewDue = evidence.lastDemonstratedAt() == null
                || Duration.between(evidence.lastDemonstratedAt(), now).toDays() >= 30;
        Band band = reviewDue && score >= 70 ? Band.REVIEW_DUE
                : score >= 85 ? Band.MASTERED
                : score >= 60 ? Band.PRACTICING : Band.DEVELOPING;
        return new Result(MODEL_VERSION, score, band, reviewDue);
    }
}
