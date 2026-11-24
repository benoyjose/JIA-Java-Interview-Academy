<?php
declare(strict_types=1);

namespace Tests\Unit;

use App\Services\LearnerReminderSchedule;
use Carbon\CarbonImmutable;
use InvalidArgumentException;
use PHPUnit\Framework\TestCase;

final class LearnerReminderScheduleTest extends TestCase
{
    public function testSchedulesInLearnerTimezoneAndMovesPastTimeToTomorrow(): void
    {
        $policy = new LearnerReminderSchedule();
        $now = CarbonImmutable::parse('2026-11-24T10:30:00+05:30');

        self::assertSame('2026-11-24T20:00:00+05:30', $policy->nextAt('20:00', 'Asia/Kolkata', $now)->toIso8601String());
        self::assertSame('2026-11-25T08:00:00+05:30', $policy->nextAt('08:00', 'Asia/Kolkata', $now)->toIso8601String());
    }

    public function testRejectsInvalidWallClock(): void
    {
        $this->expectException(InvalidArgumentException::class);
        (new LearnerReminderSchedule())->nextAt('25:90', 'Asia/Kolkata', CarbonImmutable::now());
    }

    public function testRejectsInvalidTimezone(): void
    {
        $this->expectException(InvalidArgumentException::class);
        (new LearnerReminderSchedule())->nextAt('08:00', 'Not/AZone', CarbonImmutable::now());
    }
}
