package com.jia.academy.assessment.api;

import java.util.List;
import java.util.Set;
import java.util.Map;

/** Deterministic answer normalization and exact-match grading contract. */
final class QuizAnswerEvaluator {
    private static final Set<String> SELF_REVIEW_TYPES = Set.of("DEBUGGING", "CODING", "SQL", "SCENARIO");
    private static final Set<String> OBJECTIVE_TYPES = Set.of("SINGLE_SELECT", "MULTI_SELECT", "TRUE_FALSE", "OUTPUT_PREDICTION");

    private QuizAnswerEvaluator() {}

    static boolean requiresSelfReview(String questionType) {
        return SELF_REVIEW_TYPES.contains(questionType);
    }

    static boolean isCorrect(String questionType, Map<String, Object> response, Map<String, Object> answer) {
        if (requiresSelfReview(questionType) || !OBJECTIVE_TYPES.contains(questionType)) return false;
        Object expectedValue = answer.containsKey("correctValues")
                ? answer.get("correctValues") : answer.get("correct_values");
        if (!(expectedValue instanceof List<?> expected) || expected.isEmpty()) return false;

        Object actualValue = response.get("values");
        if (actualValue == null && response.containsKey("value")) {
            Object scalar = response.get("value");
            if (scalar == null) return false;
            actualValue = List.of(scalar);
        }
        if (!(actualValue instanceof List<?> actual) || actual.isEmpty()) return false;

        List<String> expectedNormalized = expected.stream().map(String::valueOf).sorted().toList();
        List<String> actualNormalized = actual.stream().map(String::valueOf).sorted().toList();
        if ("MULTI_SELECT".equals(questionType)) {
            if (new java.util.HashSet<>(expectedNormalized).size() != expectedNormalized.size()
                    || new java.util.HashSet<>(actualNormalized).size() != actualNormalized.size()) {
                return false;
            }
            return expectedNormalized.equals(actualNormalized);
        }
        return expectedNormalized.size() == 1 && actualNormalized.size() == 1
                && expectedNormalized.equals(actualNormalized);
    }
}
