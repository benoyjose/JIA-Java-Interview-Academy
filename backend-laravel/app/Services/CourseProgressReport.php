<?php
declare(strict_types=1);
namespace App\Services;

final class CourseProgressReport
{
    /** @param array<int,array{completed:bool,score?:int|null}> $items */
    public function summarize(array $items): array
    {
        $total = count($items);
        $completed = 0;
        $scored = 0;
        $scoreTotal = 0;
        foreach ($items as $item) {
            if (($item['completed'] ?? false) === true) $completed++;
            if (isset($item['score']) && is_int($item['score']) && $item['score'] >= 0 && $item['score'] <= 100) {
                $scored++;
                $scoreTotal += $item['score'];
            }
        }
        return ['total' => $total, 'completed' => $completed,
            'completionPercent' => $total === 0 ? 0 : (int) round($completed * 100 / $total),
            'averageScore' => $scored === 0 ? null : round($scoreTotal / $scored, 2)];
    }
}
