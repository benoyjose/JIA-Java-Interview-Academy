<?php

declare(strict_types=1);

namespace App\Services;

/**
 * Suggests a sustainable next daily study goal from the previous seven local days.
 * This is a recommendation only; it never silently changes a learner's saved goal.
 */
final class LearnerGoalProgression
{
    /**
     * @param array<int, array{goal_minutes:int,completed_minutes:int}> $days
     * @return array{recommended_goal_minutes:int,qualifying_days:int,sample_days:int,reason:string}
     */
    public function recommend(int $currentGoalMinutes, array $days): array
    {
        if ($currentGoalMinutes < 5 || $currentGoalMinutes > 240) {
            throw new \InvalidArgumentException('Goal must be between 5 and 240 minutes.');
        }

        $qualifying = 0;
        foreach (array_slice($days, 0, 7) as $day) {
            $goal = $day['goal_minutes'] ?? 0;
            $completed = $day['completed_minutes'] ?? -1;
            if (!is_int($goal) || $goal < 5 || $goal > 240
                || !is_int($completed) || $completed < 0) {
                throw new \InvalidArgumentException('Daily commitment data is invalid.');
            }
            if ($completed >= $goal) {
                $qualifying++;
            }
        }

        $sample = min(7, count($days));
        if ($sample < 3) {
            return ['recommended_goal_minutes' => $currentGoalMinutes, 'qualifying_days' => $qualifying,
                'sample_days' => $sample, 'reason' => 'INSUFFICIENT_HISTORY'];
        }
        if ($qualifying >= 5) {
            return ['recommended_goal_minutes' => min(240, $currentGoalMinutes + 5), 'qualifying_days' => $qualifying,
                'sample_days' => $sample, 'reason' => $currentGoalMinutes === 240 ? 'GOAL_AT_LIMIT' : 'CONSISTENT_PROGRESS'];
        }
        if ($qualifying <= 2) {
            return ['recommended_goal_minutes' => max(5, $currentGoalMinutes - 5), 'qualifying_days' => $qualifying,
                'sample_days' => $sample, 'reason' => $currentGoalMinutes === 5 ? 'GOAL_AT_LIMIT' : 'REDUCE_FRICTION'];
        }
        return ['recommended_goal_minutes' => $currentGoalMinutes, 'qualifying_days' => $qualifying,
            'sample_days' => $sample, 'reason' => 'MAINTAIN_GOAL'];
    }
}
