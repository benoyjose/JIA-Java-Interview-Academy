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

    public function testDaytimeQuietWindowMovesOccurrenceToEndSameDay(): void
    {
        $now = CarbonImmutable::parse('2026-11-24T16:00:00+05:30');
        $next = (new LearnerReminderSchedule())->nextAt('19:00', 'Asia/Kolkata', $now, '18:00', '20:00');
        self::assertSame('2026-11-24T20:00:00+05:30', $next->toIso8601String());
    }

    public function testOvernightQuietWindowMovesEarlyOccurrenceToMorningBoundary(): void
    {
        $now = CarbonImmutable::parse('2026-11-24T06:30:00+05:30');
        $next = (new LearnerReminderSchedule())->nextAt('07:00', 'Asia/Kolkata', $now, '22:00', '08:00');
        self::assertSame('2026-11-24T08:00:00+05:30', $next->toIso8601String());
    }

    public function testOvernightOccurrenceAfterQuietStartMovesToNextMorning(): void
    {
        $now = CarbonImmutable::parse('2026-11-24T09:00:00+05:30');
        $next = (new LearnerReminderSchedule())->nextAt('07:00', 'Asia/Kolkata', $now, '22:00', '08:00');
        self::assertSame('2026-11-25T08:00:00+05:30', $next->toIso8601String());
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

    public function testQuietWindowMustBeValidAndPaired(): void
    {
        $policy = new LearnerReminderSchedule();
        $now = CarbonImmutable::parse('2026-11-24T10:00:00+05:30');
        foreach ([['22:00', null], [null, '08:00'], ['invalid', '08:00'], ['08:00', '08:00']] as [$start, $end]) {
            try {
                $policy->nextAt('19:00', 'Asia/Kolkata', $now, $start, $end);
                self::fail('Invalid quiet window should be rejected');
            } catch (InvalidArgumentException) {
                self::assertTrue(true);
            }
        }
    }
}
