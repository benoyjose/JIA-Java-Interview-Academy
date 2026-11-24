package com.jia.academy.learning.service;

import java.util.Objects;

/** Deterministic recommendation policy. Inputs are normalized client-independent learner signals. */
public final class AdaptiveRevisionPolicy {
    public enum Action { REVISIT, PRACTICE, ADVANCE }
    public record Signal(int accuracyPercent, int daysSinceLastPractice, int attempts, boolean dueForReview) {
        public Signal {
            if (accuracyPercent < 0 || accuracyPercent > 100) throw new IllegalArgumentException("accuracyPercent must be 0..100");
            if (daysSinceLastPractice < 0 || attempts < 0) throw new IllegalArgumentException("days and attempts cannot be negative");
        }
    }
    public record Recommendation(Action action, int priority, String rationale) {
        public Recommendation { Objects.requireNonNull(action); Objects.requireNonNull(rationale); }
    }
    public Recommendation recommend(Signal signal) {
        Objects.requireNonNull(signal, "signal");
        if (signal.accuracyPercent() < 60) return new Recommendation(Action.REVISIT, 100, "Accuracy is below the mastery threshold.");
        if (signal.dueForReview() || signal.daysSinceLastPractice() >= 7)
            return new Recommendation(Action.PRACTICE, 80, "The topic is due for spaced revision.");
        if (signal.accuracyPercent() < 85 || signal.attempts() < 2)
            return new Recommendation(Action.PRACTICE, 60, "More retrieval practice will stabilize mastery.");
        return new Recommendation(Action.ADVANCE, 30, "Recent accuracy supports progression.");
    }
}
