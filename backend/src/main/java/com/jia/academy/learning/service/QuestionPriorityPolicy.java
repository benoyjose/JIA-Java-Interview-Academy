package com.jia.academy.learning.service;

import java.util.Objects;

/** Stable ranking policy for selecting a learner's next question. */
public final class QuestionPriorityPolicy {
    public record Signals(int incorrectCount, int attempts, int difficulty, boolean dueForReview) {
        public Signals {
            if (incorrectCount < 0 || attempts < 0 || difficulty < 1 || difficulty > 5
                    || incorrectCount > attempts) throw new IllegalArgumentException("Invalid question signals");
        }
    }
    public int priority(Signals signals) {
        Objects.requireNonNull(signals, "signals");
        return (signals.dueForReview() ? 100 : 0)
                + Math.min(signals.incorrectCount(), 10) * 8
                + (6 - signals.difficulty()) * 3
                - Math.min(signals.attempts(), 20);
    }
}
