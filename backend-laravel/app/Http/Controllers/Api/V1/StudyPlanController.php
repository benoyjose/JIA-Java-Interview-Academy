<?php

namespace App\Http\Controllers\Api\V1;

use App\Services\StudyPlanBuilder;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;

final class StudyPlanController
{
    public function show(Request $request, StudyPlanBuilder $builder): JsonResponse
    {
        $user = $request->attributes->get('jia_user');
        abort_if($user === null, 401, 'Unauthenticated');
        $validated = $request->validate(['dailyMinutes' => ['nullable', 'integer', 'min:10', 'max:180']]);
        $academyId = '50000000-0000-0000-0000-000000000001';
        $lessons = DB::table('learner_lesson_progress as p')
            ->join('lessons as l', 'l.id', '=', 'p.lesson_id')
            ->join('categories as c', 'c.id', '=', 'l.category_id')
            ->join('technologies as t', 't.id', '=', 'c.technology_id')
            ->where('p.user_id', $user->id)->where('t.academy_id', $academyId)
            ->where('l.status', 'PUBLISHED')->where('p.progress_percent', '<', 100)
            ->orderByDesc('p.updated_at')->limit(100)
            ->get(['l.slug as lessonSlug', 'l.title', 'p.progress_percent as progressPercent']);
        $topics = DB::table('quiz_attempts as a')
            ->join('quiz_attempt_questions as aq', 'aq.attempt_id', '=', 'a.id')
            ->join('quiz_attempt_answers as aa', function ($join): void {
                $join->on('aa.attempt_id', '=', 'aq.attempt_id')->on('aa.position', '=', 'aq.position');
            })
            ->join('questions as q', 'q.id', '=', 'aq.question_id')
            ->join('categories as c', 'c.id', '=', 'q.category_id')
            ->where('a.user_id', $user->id)->where('a.academy_id', $academyId)->where('a.status', 'SUBMITTED')
            ->groupBy('c.slug', 'c.name')->orderByRaw('AVG(CASE WHEN aa.is_correct THEN 1.0 ELSE 0.0 END) ASC')
            ->limit(20)->get(['c.slug as topicSlug', 'c.name as topicName',
                DB::raw('ROUND(AVG(CASE WHEN aa.is_correct THEN 100.0 ELSE 0.0 END), 1) as accuracyPercent')]);
        return response()->json([
            'dailyMinutes' => (int) ($validated['dailyMinutes'] ?? 30),
            'generatedAt' => now()->toISOString(),
            'tasks' => $builder->build($lessons->map(fn ($row) => (array) $row)->all(),
                $topics->map(fn ($row) => (array) $row)->all(), (int) ($validated['dailyMinutes'] ?? 30)),
        ]);
    }
}
