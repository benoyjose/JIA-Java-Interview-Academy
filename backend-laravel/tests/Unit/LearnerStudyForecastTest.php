<?php

declare(strict_types=1);

namespace Tests\Unit;

use App\Services\LearnerStudyForecast;
use InvalidArgumentException;
use PHPUnit\Framework\TestCase;

final class LearnerStudyForecastTest extends TestCase
{
    public function test_forecast_scales_remaining_work_and_daily_capacity(): void
    {
        $result = (new LearnerStudyForecast())->forecast([
            ['mastery' => 50, 'estimated_minutes' => 60],
            ['mastery' => 95, 'estimated_minutes' => 40],
        ], 30);

        self::assertSame(32, $result['remaining_minutes']);
        self::assertSame(1, $result['mastered_topics']);
        self::assertSame(50, $result['completion_percent']);
        self::assertSame(2, $result['days_at_daily_minutes']);
    }

    public function test_zero_daily_capacity_is_reported_as_unknown(): void
    {
        self::assertNull((new LearnerStudyForecast())->forecast([], 0)['days_at_daily_minutes']);
    }

    public function test_invalid_mastery_is_rejected(): void
    {
        $this->expectException(InvalidArgumentException::class);
        (new LearnerStudyForecast())->forecast([['mastery' => 101, 'estimated_minutes' => 10]], 20);
    }
}
