package com.jia.academy.assessment.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class QuizAnswerEvaluatorTest {
    @Test
    void singleSelectAcceptsScalarOrSingleValueList() {
        Map<String, Object> answer = Map.of("correctValues", java.util.List.of(2));
        assertThat(QuizAnswerEvaluator.isCorrect("SINGLE_SELECT", Map.of("value", 2), answer)).isTrue();
        assertThat(QuizAnswerEvaluator.isCorrect("SINGLE_SELECT", Map.of("values", java.util.List.of("2")), answer)).isTrue();
        assertThat(QuizAnswerEvaluator.isCorrect("SINGLE_SELECT", Map.of("value", 1), answer)).isFalse();
        assertThat(QuizAnswerEvaluator.isCorrect("SINGLE_SELECT", Map.of("values", java.util.List.of(2, 3)), answer)).isFalse();
    }

    @Test
    void multiSelectIsOrderIndependentButRequiresExactSet() {
        Map<String, Object> answer = Map.of("correct_values", java.util.List.of("A", "C"));
        assertThat(QuizAnswerEvaluator.isCorrect("MULTI_SELECT", Map.of("values", java.util.List.of("C", "A")), answer)).isTrue();
        assertThat(QuizAnswerEvaluator.isCorrect("MULTI_SELECT", Map.of("values", java.util.List.of("A")), answer)).isFalse();
        assertThat(QuizAnswerEvaluator.isCorrect("MULTI_SELECT", Map.of("values", java.util.List.of("A", "B", "C")), answer)).isFalse();
    }

    @Test
    void malformedEmptyAndMissingResponsesAreIncorrectNotExceptions() {
        Map<String, Object> answer = Map.of("correctValues", java.util.List.of("yes"));
        assertThat(QuizAnswerEvaluator.isCorrect("SINGLE_SELECT", Map.of(), answer)).isFalse();
        assertThat(QuizAnswerEvaluator.isCorrect("SINGLE_SELECT", java.util.Collections.singletonMap("value", null), answer)).isFalse();
        assertThat(QuizAnswerEvaluator.isCorrect("SINGLE_SELECT", Map.of("values", java.util.List.of()), answer)).isFalse();
        assertThat(QuizAnswerEvaluator.isCorrect("SINGLE_SELECT", Map.of("value", "yes"), Map.of())).isFalse();
        assertThat(QuizAnswerEvaluator.isCorrect("MULTI_SELECT", Map.of("values", java.util.List.of("yes")), Map.of("correctValues", java.util.List.of() ))).isFalse();
    }

    @Test
    void multiSelectRejectsDuplicateValuesInSubmissionAndAnswerKey() {
        Map<String, Object> answer = Map.of("correctValues", java.util.List.of("A", "C"));
        assertThat(QuizAnswerEvaluator.isCorrect("MULTI_SELECT",
                Map.of("values", java.util.List.of("A", "A", "C")), answer)).isFalse();
        assertThat(QuizAnswerEvaluator.isCorrect("MULTI_SELECT",
                Map.of("values", java.util.List.of("A", "C")),
                Map.of("correctValues", java.util.List.of("A", "A", "C")))).isFalse();
    }
    @Test
    void codingSqlDebuggingAndScenarioRequireRubricBasedSelfReview() {
        for (String type : java.util.List.of("CODING", "SQL", "DEBUGGING", "SCENARIO")) {
            assertThat(QuizAnswerEvaluator.requiresSelfReview(type)).isTrue();
            assertThat(QuizAnswerEvaluator.isCorrect(type, Map.of("value", "alternative valid answer"),
                    Map.of("correctValues", java.util.List.of("canonical answer")))).isFalse();
        }
    }

    @Test
    void outputPredictionRemainsAutomaticallyGraded() {
        assertThat(QuizAnswerEvaluator.requiresSelfReview("OUTPUT_PREDICTION")).isFalse();
        assertThat(QuizAnswerEvaluator.isCorrect("OUTPUT_PREDICTION",
                Map.of("value", "42"), Map.of("correctValues", java.util.List.of("42")))).isTrue();
    }

}
