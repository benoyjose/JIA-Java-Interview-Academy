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
    public function nextAt(
        string $localTime,
        string $timezone,
        CarbonImmutable $now,
        ?string $quietStart = null,
        ?string $quietEnd = null
    ): CarbonImmutable {
        $this->validateWallClock($localTime, 'Reminder time');
        if (($quietStart === null) !== ($quietEnd === null)) {
            throw new InvalidArgumentException('Quiet start and end must both be set or both be omitted.');
        }
        if ($quietStart !== null && $quietEnd !== null) {
            $this->validateWallClock($quietStart, 'Quiet start');
            $this->validateWallClock($quietEnd, 'Quiet end');
            if ($quietStart === $quietEnd) {
                throw new InvalidArgumentException('Quiet start and end must differ.');
            }
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

        if ($quietStart !== null && $quietEnd !== null
            && $this->isQuiet($candidate->format('H:i'), $quietStart, $quietEnd)) {
            // For a daytime window the end is today; for an overnight window
            // an occurrence after quietStart exits quiet time tomorrow.
            $endDate = $quietStart < $quietEnd || $candidate->format('H:i') < $quietStart
                ? $candidate->startOfDay()
                : $candidate->startOfDay()->addDay();
            $candidate = $endDate->setTime(...array_map('intval', explode(':', $quietEnd)));
            if ($candidate->lessThanOrEqualTo($localNow)) {
                $candidate = $candidate->addDay();
            }
        }

        return $candidate;
    }

    private function validateWallClock(string $value, string $label): void
    {
        if (!preg_match('/^(?:[01]\d|2[0-3]):[0-5]\d$/', $value)) {
            throw new InvalidArgumentException($label . ' must use HH:mm (24-hour) format.');
        }
    }

    private function isQuiet(string $time, string $start, string $end): bool
    {
        return $start < $end
            ? $time >= $start && $time < $end
            : $time >= $start || $time < $end;
    }
}
