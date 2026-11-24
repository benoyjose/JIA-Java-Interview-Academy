<?php

declare(strict_types=1);

namespace App\Services;

/**
 * Deterministic forecast for a learner's remaining study workload.
 * Input mastery values are percentages; no client-provided completion is persisted.
 */
final class LearnerStudyForecast
{
    /**
     * @param array<int, array{mastery: int, estimated_minutes: int}> $topics
     * @return array{remaining_minutes: int, mastered_topics: int, total_topics: int, completion_percent: int, days_at_daily_minutes: int|null}
     */
    public function forecast(array $topics, int $dailyMinutes): array
    {
        if ($dailyMinutes < 0 || $dailyMinutes > 1440) {
            throw new \InvalidArgumentException('Daily minutes must be between 0 and 1440.');
        }

        $remaining = 0;
        $mastered = 0;
        foreach ($topics as $topic) {
            $mastery = $topic['mastery'] ?? -1;
            $minutes = $topic['estimated_minutes'] ?? -1;
            if (!is_int($mastery) || $mastery < 0 || $mastery > 100
                || !is_int($minutes) || $minutes < 0) {
                throw new \InvalidArgumentException('Topic mastery or duration is invalid.');
            }
            if ($mastery >= 90) {
                $mastered++;
            }
            $remaining += (int) ceil($minutes * (100 - $mastery) / 100);
        }

        $count = count($topics);
        return [
            'remaining_minutes' => $remaining,
            'mastered_topics' => $mastered,
            'total_topics' => $count,
            'completion_percent' => $count === 0 ? 0 : (int) floor($mastered * 100 / $count),
            'days_at_daily_minutes' => $dailyMinutes === 0 ? null : (int) ceil($remaining / $dailyMinutes),
        ];
    }
}
