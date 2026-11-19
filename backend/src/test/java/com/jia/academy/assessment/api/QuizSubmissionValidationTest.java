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
    @Test
    void acceptsNullOrNonNegativeIntegerTimeSpent() {
        assertThatCode(() -> QuizController.validateTimeSpentMs(null)).doesNotThrowAnyException();
        assertThatCode(() -> QuizController.validateTimeSpentMs(0)).doesNotThrowAnyException();
        assertThatCode(() -> QuizController.validateTimeSpentMs(1250L)).doesNotThrowAnyException();
    }

    @Test
    void rejectsNegativeFractionalAndNonNumericTimeSpent() {
        assertThatThrownBy(() -> QuizController.validateTimeSpentMs(-1))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> QuizController.validateTimeSpentMs(1.5))
                .isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> QuizController.validateTimeSpentMs("100"))
                .isInstanceOf(ResponseStatusException.class);
    }

}
