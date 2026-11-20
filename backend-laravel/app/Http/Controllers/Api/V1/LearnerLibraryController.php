<?php

namespace App\Http\Controllers\Api\V1;

use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Symfony\Component\HttpKernel\Exception\BadRequestHttpException;
use Symfony\Component\HttpKernel\Exception\ConflictHttpException;
use Symfony\Component\HttpKernel\Exception\NotFoundHttpException;

final class LearnerLibraryController
{
    public function progress(Request $request): JsonResponse
    {
        $user = $request->attributes->get('jia_user');
        abort_if($user === null, 401, 'Unauthenticated');

        $rows = DB::table('learner_lesson_progress')
            ->join('lessons', 'lessons.id', '=', 'learner_lesson_progress.lesson_id')
            ->where('learner_lesson_progress.user_id', $user->id)
            ->orderByDesc('learner_lesson_progress.updated_at')
            ->limit(200)
            ->get(['lessons.slug', 'learner_lesson_progress.content_revision',
                'learner_lesson_progress.status', 'learner_lesson_progress.progress_percent',
                'learner_lesson_progress.last_position_json', 'learner_lesson_progress.updated_at']);

        return response()->json($rows->map(fn (object $row): array => [
            'lessonSlug' => $row->slug,
            'contentRevision' => (int) $row->content_revision,
            'status' => $row->status,
            'progressPercent' => (float) $row->progress_percent,
            'lastPosition' => $row->last_position_json === null ? null : json_decode($row->last_position_json, true),
            'updatedAt' => $row->updated_at,
        ])->values());
    }

    public function updateProgress(Request $request, string $lessonSlug): JsonResponse
    {
        $validated = $request->validate([
            'contentRevision' => ['required', 'integer', 'min:1'],
            'progressPercent' => ['required', 'numeric', 'min:0', 'max:100'],
            'lastPosition' => ['nullable', 'array'],
        ]);
        $user = $request->attributes->get('jia_user');
        abort_if($user === null, 401, 'Unauthenticated');

        return DB::transaction(function () use ($validated, $user, $lessonSlug): JsonResponse {
            $lesson = DB::table('lessons')->where('slug', $lessonSlug)->where('status', 'PUBLISHED')
                ->first(['id', 'published_revision_id']);
            if ($lesson === null) throw new NotFoundHttpException('Lesson not found');
            $currentRevision = DB::table('lesson_revisions')->where('id', $lesson->published_revision_id)->value('revision_number');
            if ($currentRevision === null || (int) $validated['contentRevision'] > (int) $currentRevision) {
                throw new ConflictHttpException('Unknown lesson revision');
            }
            $completed = (float) $validated['progressPercent'] >= 100;
            $now = now();
            DB::statement(<<<'SQL'
                INSERT INTO learner_lesson_progress
                    (user_id, lesson_id, content_revision, status, progress_percent, last_position_json, completed_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?::jsonb, CASE WHEN ? THEN ? ELSE NULL END, ?)
                ON CONFLICT (user_id, lesson_id) DO UPDATE SET
                    content_revision = EXCLUDED.content_revision,
                    status = CASE WHEN learner_lesson_progress.status = 'COMPLETED' THEN 'COMPLETED' ELSE EXCLUDED.status END,
                    progress_percent = GREATEST(learner_lesson_progress.progress_percent, EXCLUDED.progress_percent),
                    last_position_json = COALESCE(EXCLUDED.last_position_json, learner_lesson_progress.last_position_json),
                    completed_at = COALESCE(learner_lesson_progress.completed_at, EXCLUDED.completed_at),
                    updated_at = EXCLUDED.updated_at
                SQL, [
                    $user->id, $lesson->id, $validated['contentRevision'],
                    $completed ? 'COMPLETED' : 'IN_PROGRESS', $validated['progressPercent'],
                    isset($validated['lastPosition']) ? json_encode($validated['lastPosition'], JSON_THROW_ON_ERROR) : null,
                    $completed, $completed ? $now : null, $now,
                ]);

            return response()->json([
                'lessonSlug' => $lessonSlug,
                'contentRevision' => (int) $validated['contentRevision'],
                'status' => $completed ? 'COMPLETED' : 'IN_PROGRESS',
                'progressPercent' => (float) $validated['progressPercent'],
            ]);
        });
    }

    public function bookmarks(Request $request): JsonResponse
    {
        $user = $request->attributes->get('jia_user');
        abort_if($user === null, 401, 'Unauthenticated');
        $rows = DB::table('learner_bookmarks')
            ->join('lessons', 'lessons.id', '=', 'learner_bookmarks.lesson_id')
            ->where('learner_bookmarks.user_id', $user->id)
            ->orderByDesc('learner_bookmarks.created_at')->limit(200)
            ->get(['lessons.slug', 'lessons.title', 'learner_bookmarks.created_at']);
        return response()->json($rows->map(fn (object $row): array => [
            'lessonSlug' => $row->slug, 'title' => $row->title, 'createdAt' => $row->created_at,
        ])->values());
    }

    public function bookmark(Request $request, string $lessonSlug): JsonResponse
    {
        $user = $request->attributes->get('jia_user');
        abort_if($user === null, 401, 'Unauthenticated');
        $lesson = DB::table('lessons')->where('slug', $lessonSlug)->where('status', 'PUBLISHED')->first(['id']);
        if ($lesson === null) throw new NotFoundHttpException('Lesson not found');
        DB::table('learner_bookmarks')->insertOrIgnore(['user_id' => $user->id, 'lesson_id' => $lesson->id]);
        return response()->json(['lessonSlug' => $lessonSlug, 'bookmarked' => true]);
    }

    public function removeBookmark(Request $request, string $lessonSlug): JsonResponse
    {
        $user = $request->attributes->get('jia_user');
        abort_if($user === null, 401, 'Unauthenticated');
        DB::table('learner_bookmarks')
            ->where('user_id', $user->id)
            ->whereIn('lesson_id', DB::table('lessons')->select('id')->where('slug', $lessonSlug))
            ->delete();
        return response()->json(['lessonSlug' => $lessonSlug, 'bookmarked' => false]);
    }
}
