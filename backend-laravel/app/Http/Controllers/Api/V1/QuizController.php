<?php

namespace App\Http\Controllers\Api\V1;

use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;
use Illuminate\Validation\Rule;
use Symfony\Component\HttpKernel\Exception\ConflictHttpException;
use Symfony\Component\HttpKernel\Exception\NotFoundHttpException;
use Symfony\Component\HttpKernel\Exception\UnprocessableEntityHttpException;

final class QuizController
{
    private const ACADEMY_ID = '50000000-0000-0000-0000-000000000001';
    private const DIFFICULTIES = ['BEGINNER', 'NOVICE', 'PROFESSIONAL', 'EXPERT'];
    private const MODES = ['PRACTICE', 'TIMED_MOCK', 'DAILY_CHALLENGE', 'WEAK_AREA'];

    public function availability(Request $request): JsonResponse
    {
        $filters = $this->filters($request);
        $count = $this->questionQuery($filters)->count();

        return response()->json([
            'difficulty' => $filters['difficulty'],
            'availableQuestionCount' => $count,
        ]);
    }

    public function start(Request $request): JsonResponse
    {
        $validated = $request->validate([
            'difficulty' => ['required', Rule::in(self::DIFFICULTIES)],
            'technologySlug' => ['nullable', 'string', 'max:80'],
            'categorySlug' => ['nullable', 'string', 'max:100'],
            'questionCount' => ['nullable', 'integer', 'min:1', 'max:100'],
            'mode' => ['nullable', Rule::in(self::MODES)],
        ]);
        $filters = $this->filters($request);
        $count = (int) ($validated['questionCount'] ?? 10);
        $user = $request->attributes->get('jia_user');
        abort_if($user === null, 401, 'Unauthenticated');

        return DB::transaction(function () use ($filters, $count, $validated, $user): JsonResponse {
            $selected = $this->questionQuery($filters)
                ->inRandomOrder()
                ->limit($count)
                ->get([
                    'questions.id as question_id',
                    'questions.question_type',
                    'questions.difficulty',
                    'questions.current_revision',
                    'question_revisions.id as revision_id',
                    'question_revisions.prompt_json',
                    'question_revisions.options_json',
                    'question_revisions.hint',
                ]);

            if ($selected->count() < $count) {
                throw new UnprocessableEntityHttpException(
                    'Not enough published questions for the selected filters. Available: '.$selected->count()
                );
            }

            $attemptId = (string) Str::uuid();
            $startedAt = now();
            DB::table('quiz_attempts')->insert([
                'id' => $attemptId,
                'user_id' => $user->id,
                'academy_id' => self::ACADEMY_ID,
                'technology_id' => $filters['technologyId'],
                'category_id' => $filters['categoryId'],
                'difficulty' => $filters['difficulty'],
                'mode' => $validated['mode'] ?? 'PRACTICE',
                'status' => 'IN_PROGRESS',
                'question_count' => $count,
                'started_at' => $startedAt,
            ]);

            $questions = [];
            foreach ($selected->values() as $index => $row) {
                $position = $index + 1;
                DB::table('quiz_attempt_questions')->insert([
                    'attempt_id' => $attemptId,
                    'question_id' => $row->question_id,
                    'question_revision_id' => $row->revision_id,
                    'position' => $position,
                    'max_marks' => 1,
                ]);
                $questions[] = [
                    'position' => $position,
                    'questionId' => $row->question_id,
                    'revisionId' => $row->revision_id,
                    'questionType' => $row->question_type,
                    'prompt' => $this->decodeJson($row->prompt_json),
                    'options' => $this->decodeJson($row->options_json),
                    'hint' => $row->hint,
                    'difficulty' => $row->difficulty,
                ];
            }

            return response()->json([
                'attemptId' => $attemptId,
                'difficulty' => $filters['difficulty'],
                'mode' => $validated['mode'] ?? 'PRACTICE',
                'questionCount' => $count,
                'questions' => $questions,
            ], 201);
        });
    }

    public function submit(Request $request, string $attemptId): JsonResponse
    {
        $validated = $request->validate([
            'answers' => ['required', 'array', 'min:1'],
            'answers.*.position' => ['required', 'integer', 'min:1'],
            'answers.*.response' => ['required', 'array'],
            'answers.*.timeSpentMs' => ['nullable', 'integer', 'min:0'],
        ]);
        $user = $request->attributes->get('jia_user');
        abort_if($user === null, 401, 'Unauthenticated');

        return DB::transaction(function () use ($attemptId, $validated, $user): JsonResponse {
            $attempt = DB::table('quiz_attempts')->where('id', $attemptId)
                ->where('user_id', $user->id)->lockForUpdate()->first();
            if ($attempt === null) {
                throw new NotFoundHttpException('Quiz attempt not found');
            }
            if ($attempt->status !== 'IN_PROGRESS') {
                throw new ConflictHttpException('Quiz attempt is already submitted or closed');
            }

            $submitted = collect($validated['answers'])->keyBy('position');
            $questions = DB::table('quiz_attempt_questions')
                ->join('question_revisions', 'question_revisions.id', '=', 'quiz_attempt_questions.question_revision_id')
                ->join('questions', 'questions.id', '=', 'quiz_attempt_questions.question_id')
                ->where('quiz_attempt_questions.attempt_id', $attemptId)
                ->orderBy('quiz_attempt_questions.position')
                ->get([
                    'quiz_attempt_questions.position',
                    'questions.question_type',
                    'question_revisions.answer_json',
                    'question_revisions.explanation',
                ]);

            $correct = 0;
            $answerResults = [];
            foreach ($questions as $question) {
                $submittedAnswer = $submitted->get($question->position);
                $response = $submittedAnswer['response'] ?? [];
                $key = $this->decodeJson($question->answer_json) ?? [];
                $isCorrect = $submittedAnswer !== null && $this->isCorrect($question->question_type, $response, $key);
                if ($isCorrect) {
                    $correct++;
                }
                $timeSpent = $submittedAnswer['timeSpentMs'] ?? null;
                DB::table('quiz_attempt_answers')->updateOrInsert(
                    ['attempt_id' => $attemptId, 'position' => $question->position],
                    [
                        'response_json' => json_encode($response, JSON_THROW_ON_ERROR),
                        'is_correct' => $isCorrect,
                        'marks_awarded' => $isCorrect ? 1 : 0,
                        'time_spent_ms' => $timeSpent,
                        'answered_at' => now(),
                    ]
                );
                $answerResults[] = [
                    'position' => $question->position,
                    'isCorrect' => $isCorrect,
                    'explanation' => $question->explanation,
                ];
            }

            $total = $questions->count();
            $score = $total === 0 ? 0 : round(($correct / $total) * 100, 2);
            $submittedAt = now();
            DB::table('quiz_attempts')->where('id', $attemptId)->update([
                'status' => 'SUBMITTED',
                'correct_count' => $correct,
                'score_percent' => $score,
                'submitted_at' => $submittedAt,
                'duration_seconds' => max(0, $submittedAt->diffInSeconds($attempt->started_at)),
            ]);

            return response()->json([
                'attemptId' => $attemptId,
                'difficulty' => $attempt->difficulty,
                'questionCount' => $total,
                'correctCount' => $correct,
                'scorePercent' => $score,
                'submittedAt' => $submittedAt->toISOString(),
                'answers' => $answerResults,
            ]);
        });
    }

    public function performance(Request $request): JsonResponse
    {
        $user = $request->attributes->get('jia_user');
        abort_if($user === null, 401, 'Unauthenticated');

        $byDifficulty = DB::table('quiz_attempts')
            ->where('user_id', $user->id)->where('academy_id', self::ACADEMY_ID)
            ->where('status', 'SUBMITTED')
            ->selectRaw('difficulty, COUNT(*) AS attempts, SUM(question_count) AS questions_attempted, SUM(correct_count) AS correct_answers, ROUND((SUM(correct_count)::numeric / NULLIF(SUM(question_count), 0)) * 100, 2) AS accuracy_percent, MAX(score_percent) AS best_score_percent, MAX(submitted_at) AS latest_attempt_at')
            ->groupBy('difficulty')->get()->keyBy('difficulty');

        $difficultyRows = array_map(function (string $level) use ($byDifficulty): array {
            $row = $byDifficulty->get($level);
            return [
                'difficulty' => $level,
                'attempts' => (int) ($row->attempts ?? 0),
                'questionsAttempted' => (int) ($row->questions_attempted ?? 0),
                'correctAnswers' => (int) ($row->correct_answers ?? 0),
                'accuracyPercent' => $row?->accuracy_percent === null ? null : (float) $row->accuracy_percent,
                'bestScorePercent' => $row?->best_score_percent === null ? null : (float) $row->best_score_percent,
                'latestAttemptAt' => $row?->latest_attempt_at,
            ];
        }, self::DIFFICULTIES);

        $byTopic = DB::table('quiz_attempts')
            ->leftJoin('technologies', 'technologies.id', '=', 'quiz_attempts.technology_id')
            ->leftJoin('categories', 'categories.id', '=', 'quiz_attempts.category_id')
            ->where('quiz_attempts.user_id', $user->id)
            ->where('quiz_attempts.academy_id', self::ACADEMY_ID)
            ->where('quiz_attempts.status', 'SUBMITTED')
            ->selectRaw('technologies.slug AS technology_slug, categories.slug AS category_slug, quiz_attempts.difficulty, COUNT(*) AS attempts, ROUND((SUM(quiz_attempts.correct_count)::numeric / NULLIF(SUM(quiz_attempts.question_count), 0)) * 100, 2) AS accuracy_percent')
            ->groupBy('technologies.slug', 'categories.slug', 'quiz_attempts.difficulty')
            ->get()
            ->map(fn (object $row): array => [
                'technologySlug' => $row->technology_slug,
                'categorySlug' => $row->category_slug,
                'difficulty' => $row->difficulty,
                'attempts' => (int) $row->attempts,
                'accuracyPercent' => (float) $row->accuracy_percent,
            ])->values();

        return response()->json(['byDifficulty' => $difficultyRows, 'byTopic' => $byTopic]);
    }

    public function history(Request $request): JsonResponse
    {
        $user = $request->attributes->get('jia_user');
        abort_if($user === null, 401, 'Unauthenticated');
        $validated = $request->validate([
            'difficulty' => ['nullable', Rule::in(self::DIFFICULTIES)],
            'limit' => ['nullable', 'integer', 'min:1', 'max:100'],
        ]);

        $query = DB::table('quiz_attempts')
            ->leftJoin('technologies', 'technologies.id', '=', 'quiz_attempts.technology_id')
            ->leftJoin('categories', 'categories.id', '=', 'quiz_attempts.category_id')
            ->where('quiz_attempts.user_id', $user->id)
            ->where('quiz_attempts.academy_id', self::ACADEMY_ID)
            ->orderByDesc('quiz_attempts.started_at')
            ->limit((int) ($validated['limit'] ?? 20));
        if (isset($validated['difficulty'])) {
            $query->where('quiz_attempts.difficulty', $validated['difficulty']);
        }

        return response()->json($query->get([
            'quiz_attempts.id', 'quiz_attempts.difficulty', 'technologies.slug as technology_slug',
            'categories.slug as category_slug', 'quiz_attempts.question_count',
            'quiz_attempts.correct_count', 'quiz_attempts.score_percent',
            'quiz_attempts.started_at', 'quiz_attempts.status',
        ])->map(fn (object $row): array => [
            'attemptId' => $row->id,
            'difficulty' => $row->difficulty,
            'technologySlug' => $row->technology_slug,
            'categorySlug' => $row->category_slug,
            'questionCount' => (int) $row->question_count,
            'correctCount' => $row->correct_count === null ? null : (int) $row->correct_count,
            'scorePercent' => $row->score_percent === null ? null : (float) $row->score_percent,
            'startedAt' => $row->started_at,
            'status' => $row->status,
        ])->values());
    }

    private function filters(Request $request): array
    {
        $validated = $request->validate([
            'difficulty' => ['required', Rule::in(self::DIFFICULTIES)],
            'technologySlug' => ['nullable', 'string', 'max:80'],
            'categorySlug' => ['nullable', 'string', 'max:100'],
        ]);
        $technologyId = null;
        $categoryId = null;
        if (! empty($validated['technologySlug'])) {
            $technologyId = DB::table('technologies')->where('academy_id', self::ACADEMY_ID)
                ->where('slug', $validated['technologySlug'])->where('status', 'PUBLISHED')->value('id');
            if ($technologyId === null) {
                throw new NotFoundHttpException('Technology not found');
            }
        }
        if (! empty($validated['categorySlug'])) {
            $category = DB::table('categories')->join('technologies', 'technologies.id', '=', 'categories.technology_id')
                ->where('categories.slug', $validated['categorySlug'])
                ->where('categories.status', 'PUBLISHED')->where('technologies.academy_id', self::ACADEMY_ID)
                ->first(['categories.id', 'categories.technology_id']);
            if ($category === null || ($technologyId !== null && $category->technology_id !== $technologyId)) {
                throw new NotFoundHttpException('Category not found for selected technology');
            }
            $technologyId = $category->technology_id;
            $categoryId = $category->id;
        }

        return [
            'difficulty' => $validated['difficulty'],
            'technologyId' => $technologyId,
            'categoryId' => $categoryId,
        ];
    }

    private function questionQuery(array $filters): \Illuminate\Database\Query\Builder
    {
        $query = DB::table('questions')
            ->join('technologies', 'technologies.id', '=', 'questions.technology_id')
            ->join('question_revisions', function ($join): void {
                $join->on('question_revisions.question_id', '=', 'questions.id')
                    ->on('question_revisions.revision_number', '=', 'questions.current_revision');
            })
            ->where('questions.academy_id', self::ACADEMY_ID)
            ->where('technologies.academy_id', self::ACADEMY_ID)
            ->where('questions.status', 'PUBLISHED')
            ->where('questions.difficulty', $filters['difficulty']);
        if ($filters['technologyId'] !== null) {
            $query->where('questions.technology_id', $filters['technologyId']);
        }
        if ($filters['categoryId'] !== null) {
            $query->where('questions.category_id', $filters['categoryId']);
        }
        return $query;
    }

    private function decodeJson(mixed $value): mixed
    {
        if ($value === null || is_array($value)) {
            return $value;
        }
        return json_decode((string) $value, true, 512, JSON_THROW_ON_ERROR);
    }

    private function isCorrect(string $type, array $response, array $answer): bool
    {
        $expected = $answer['correctValues'] ?? $answer['correct_values'] ?? null;
        if (! is_array($expected)) {
            return false;
        }
        $actual = $response['values'] ?? (isset($response['value']) ? [$response['value']] : null);
        if (! is_array($actual)) {
            return false;
        }
        $expected = array_map('strval', $expected);
        $actual = array_map('strval', $actual);
        if ($type === 'MULTI_SELECT') {
            sort($expected);
            sort($actual);
            return $expected === $actual;
        }
        return count($expected) === 1 && count($actual) === 1 && $expected[0] === $actual[0];
    }
}
