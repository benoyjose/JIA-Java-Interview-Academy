package com.jia.academy.assessment.api;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class QuizSubmissionValidationTest {
    private static final Set<Integer> EXPECTED = Set.of(1, 2, 3);

    @Test
    void acceptsPracticeMode() {
        assertThatCode(() -> QuizController.validateMode("PRACTICE"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsModesUntilTheirWorkflowsAreImplemented() {
        for (String mode : Set.of("TIMED_MOCK", "DAILY_CHALLENGE", "WEAK_AREA", "UNKNOWN")) {
            assertThatThrownBy(() -> QuizController.validateMode(mode))
                    .isInstanceOf(ResponseStatusException.class)
                    .hasMessageContaining("Quiz mode is not available yet");
        }
    }

    @Test
    void rejectsNullMode() {
        assertThatThrownBy(() -> QuizController.validateMode(null))
                .isInstanceOf(ResponseStatusException.class);
    }

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
