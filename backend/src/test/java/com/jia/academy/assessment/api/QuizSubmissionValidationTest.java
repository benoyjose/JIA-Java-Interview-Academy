package com.jia.academy.assessment.api;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class QuizSubmissionValidationTest {
    private static final Set<Integer> EXPECTED = Set.of(1, 2, 3);

    @Test
    void acceptsExactlyOneAnswerForEveryAttemptQuestion() {
        assertThatCode(() -> QuizController.validateCompleteAnswers(EXPECTED, Set.of(1, 2, 3)))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingAnswers() {
        assertThatThrownBy(() -> QuizController.validateCompleteAnswers(EXPECTED, Set.of(1, 2)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rejectsAnswersForPositionsOutsideAttempt() {
        assertThatThrownBy(() -> QuizController.validateCompleteAnswers(EXPECTED, Set.of(1, 2, 4)))
                .isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rejectsEmptyAttemptQuestionSet() {
        assertThatThrownBy(() -> QuizController.validateCompleteAnswers(Set.of(), Set.of()))
                .isInstanceOf(ResponseStatusException.class);
    }
}
