<?php
declare(strict_types=1);
namespace Tests\\Unit;
use App\\Services\\LearnerReminderSchedule;
use Carbon\\CarbonImmutable;
use InvalidArgumentException;
use PHPUnit\\Framework\\TestCase;

final class LearnerReminderScheduleTest extends TestCase
{
    public function testSchedulesInTheLearnersTimezoneAndMovesPastTimeToTomorrow(): void
    {
        $policy = new LearnerReminderSchedule();
        $now = CarbonImmutable::parse('2026-11-24T10:30:00+05:30');
        self::assertSame('2026-11-24T20:00:00+05:30', $policy->nextAt('20:00', 'Asia/Kolkata', $now)->toIso8601String());
        self::assertSame('2026-11-25T08:00:00+05:30', $policy->nextAt('08:00', 'Asia/Kolkata', $now)->toIso8601String());
    }
    public function testRejectsInvalidWallClockAndTimezone(): void
    {
        $policy = new LearnerReminderSchedule();
        $this->expectException(InvalidArgumentException::class);
        $policy->nextAt('25:90', 'Not/AZone', CarbonImmutable::now());
    }
}
