<?php

namespace Tests\Unit;

use App\Services\StudyPlanBuilder;
use PHPUnit\Framework\TestCase;

final class StudyPlanBuilderTest extends TestCase
{
    public function test_plan_prioritizes_weak_topics_and_respects_time_budget(): void
    {
        $plan = (new StudyPlanBuilder())->build(
            [['lessonSlug' => 'streams', 'title' => 'Streams', 'progressPercent' => 20]],
            [['topicSlug' => 'concurrency', 'topicName' => 'Concurrency', 'accuracyPercent' => 35],
             ['topicSlug' => 'collections', 'topicName' => 'Collections', 'accuracyPercent' => 95]],
            20,
        );
        self::assertSame('concurrency', $plan[0]['topicSlug']);
        self::assertSame(20, array_sum(array_column($plan, 'minutes')));
        self::assertCount(2, $plan);
    }

    public function test_plan_clamps_daily_minutes_and_skips_completed_or_mastered_items(): void
    {
        $plan = (new StudyPlanBuilder())->build(
            [['lessonSlug' => 'done', 'title' => 'Done', 'progressPercent' => 100]],
            [['topicSlug' => 'mastered', 'topicName' => 'Mastered', 'accuracyPercent' => 100]],
            1,
        );
        self::assertSame([], $plan);
    }
}
