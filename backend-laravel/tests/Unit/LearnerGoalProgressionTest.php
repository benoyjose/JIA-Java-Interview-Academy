<?php

declare(strict_types=1);

namespace Tests\Unit;

use App\Services\LearnerGoalProgression;
use PHPUnit\Framework\TestCase;

final class LearnerGoalProgressionTest extends TestCase
{
    public function testConsistentWeekRaisesGoalByFiveMinutes(): void
    {
        $days = array_fill(0, 7, ['goal_minutes' => 30, 'completed_minutes' => 35]);
        $result = (new LearnerGoalProgression())->recommend(30, $days);
        self::assertSame(35, $result['recommended_goal_minutes']);
        self::assertSame('CONSISTENT_PROGRESS', $result['reason']);
    }

    public function testLowCompletionReducesGoalWithoutGoingBelowFive(): void
    {
        $days = array_fill(0, 7, ['goal_minutes' => 10, 'completed_minutes' => 0]);
        self::assertSame(5, (new LearnerGoalProgression())->recommend(7, $days)['recommended_goal_minutes']);
    }

    public function testInsufficientHistoryNeverChangesGoal(): void
    {
        $result = (new LearnerGoalProgression())->recommend(45, [
            ['goal_minutes' => 45, 'completed_minutes' => 45],
            ['goal_minutes' => 45, 'completed_minutes' => 45],
        ]);
        self::assertSame(45, $result['recommended_goal_minutes']);
        self::assertSame('INSUFFICIENT_HISTORY', $result['reason']);
    }

    public function testInvalidGoalOrHistoryIsRejected(): void
    {
        $policy = new LearnerGoalProgression();
        $this->expectException(\InvalidArgumentException::class);
        $policy->recommend(0, []);
    }

    public function testGoalIsCappedAtTwoHundredFortyMinutes(): void
    {
        $days = array_fill(0, 7, ['goal_minutes' => 240, 'completed_minutes' => 240]);
        self::assertSame(240, (new LearnerGoalProgression())->recommend(240, $days)['recommended_goal_minutes']);
    }
}
