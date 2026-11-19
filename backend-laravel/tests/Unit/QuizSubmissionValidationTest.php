<?php

namespace Tests\Unit;

use App\Http\Controllers\Api\V1\QuizController;
use PHPUnit\Framework\TestCase;
use Symfony\Component\HttpKernel\Exception\BadRequestHttpException;

final class QuizSubmissionValidationTest extends TestCase
{
    public function test_accepts_practice_mode(): void
    {
        QuizController::assertAvailableMode('PRACTICE');
        $this->assertTrue(true);
    }

    public function test_rejects_modes_until_their_workflows_are_implemented(): void
    {
        foreach (['TIMED_MOCK', 'DAILY_CHALLENGE', 'WEAK_AREA', 'UNKNOWN'] as $mode) {
            try {
                QuizController::assertAvailableMode($mode);
                $this->fail("Expected mode {$mode} to be unavailable.");
            } catch (BadRequestHttpException $exception) {
                $this->assertStringContainsString('Quiz mode is not available yet', $exception->getMessage());
            }
        }
    }

    public function test_accepts_exact_complete_answer_positions(): void
    {
        QuizController::assertCompleteAnswerPositions([1, 2, 3], [1, 2, 3]);
        $this->assertTrue(true);
    }

    public function test_rejects_missing_answer_positions(): void
    {
        $this->expectException(BadRequestHttpException::class);
        QuizController::assertCompleteAnswerPositions([1, 2, 3], [1, 2]);
    }

    public function test_rejects_out_of_attempt_positions(): void
    {
        $this->expectException(BadRequestHttpException::class);
        QuizController::assertCompleteAnswerPositions([1, 2, 3], [1, 2, 4]);
    }

    public function test_rejects_empty_attempt_question_set(): void
    {
        $this->expectException(BadRequestHttpException::class);
        QuizController::assertCompleteAnswerPositions([], []);
    }
}
