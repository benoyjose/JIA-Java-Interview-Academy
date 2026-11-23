<?php

namespace App\Services;

final class StudyPlanBuilder
{
    /**
     * Turn unfinished lessons and low-accuracy topics into a deterministic,
     * explainable daily plan. The service deliberately never invents content.
     *
     * @param array<int, array<string, mixed>> $lessons
     * @param array<int, array<string, mixed>> $topics
     * @return array<int, array<string, mixed>>
     */
    public function build(array $lessons, array $topics, int $dailyMinutes = 30): array
    {
        $minutes = max(10, min(180, $dailyMinutes));
        $tasks = [];
        foreach ($topics as $topic) {
            $accuracy = isset($topic['accuracyPercent']) ? (float) $topic['accuracyPercent'] : null;
            if ($accuracy !== null && $accuracy >= 80) {
                continue;
            }
            $tasks[] = [
                'kind' => 'PRACTICE_TOPIC',
                'topicSlug' => (string) $topic['topicSlug'],
                'title' => 'Practice '.$topic['topicName'],
                'minutes' => 10,
                'reason' => $accuracy === null ? 'Build a baseline' : 'Accuracy is '.round($accuracy).'%; reinforce this topic',
                'priority' => $accuracy === null ? 40 : max(1, 100 - $accuracy),
            ];
        }
        foreach ($lessons as $lesson) {
            $progress = (float) ($lesson['progressPercent'] ?? 0);
            if ($progress >= 100) {
                continue;
            }
            $tasks[] = [
                'kind' => 'CONTINUE_LESSON',
                'lessonSlug' => (string) $lesson['lessonSlug'],
                'title' => 'Continue '.$lesson['title'],
                'minutes' => 15,
                'reason' => $progress > 0 ? 'Resume from '.round($progress).'% complete' : 'Start a recommended lesson',
                'priority' => 25 + (int) round($progress / 4),
            ];
        }
        usort($tasks, static fn (array $a, array $b): int => $b['priority'] <=> $a['priority']);
        $budget = $minutes;
        $plan = [];
        foreach ($tasks as $task) {
            if ($budget <= 0) break;
            $task['minutes'] = min($task['minutes'], $budget);
            unset($task['priority']);
            $plan[] = $task;
            $budget -= $task['minutes'];
        }
        return $plan;
    }
}
