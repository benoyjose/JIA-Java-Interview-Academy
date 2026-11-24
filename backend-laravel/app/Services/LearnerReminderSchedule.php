<?php
declare(strict_types=1);

namespace App\Services;

use Carbon\CarbonImmutable;
use DateTimeZone;
use InvalidArgumentException;
use Throwable;

/** Computes the next reminder occurrence in the learner's local timezone. */
final class LearnerReminderSchedule
{
    public function nextAt(string $localTime, string $timezone, CarbonImmutable $now): CarbonImmutable
    {
        if (!preg_match('/^(?:[01]\d|2[0-3]):[0-5]\d$/', $localTime)) {
            throw new InvalidArgumentException('Reminder time must use HH:mm (24-hour) format.');
        }

        try {
            $zone = new DateTimeZone($timezone);
        } catch (Throwable $error) {
            throw new InvalidArgumentException('A valid IANA timezone is required.', 0, $error);
        }

        [$hour, $minute] = array_map('intval', explode(':', $localTime));
        $localNow = $now->setTimezone($zone);
        $candidate = $localNow->startOfDay()->setTime($hour, $minute);

        if ($candidate->lessThanOrEqualTo($localNow)) {
            $candidate = $candidate->addDay()->setTime($hour, $minute);
        }

        return $candidate;
    }
}
