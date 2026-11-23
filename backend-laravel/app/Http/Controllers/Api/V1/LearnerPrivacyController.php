<?php

namespace App\Http\Controllers\Api\V1;

use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;

final class LearnerPrivacyController
{
    public function export(Request $request): JsonResponse
    {
        $user = $request->attributes->get('jia_user');
        abort_if($user === null, 401, 'Unauthenticated');
        $id = $user->id;

        // Every collection is scoped to the token's user. Do not include secrets,
        // global authored question/answer-key material, or other users' records.
        return response()->json([
            'formatVersion' => 1,
            'generatedAt' => now()->toISOString(),
            'profile' => DB::table('users')->where('id', $id)
                ->get(['id', 'name', 'email', 'status']),
            'lessonProgress' => DB::table('learner_lesson_progress')->where('user_id', $id)
                ->orderByDesc('updated_at')->get(['lesson_id', 'content_revision', 'status',
                    'progress_percent', 'last_position_json', 'started_at', 'completed_at', 'updated_at']),
            'bookmarks' => DB::table('learner_bookmarks')->where('user_id', $id)
                ->orderByDesc('created_at')->get(['lesson_id', 'created_at']),
            'quizAttempts' => DB::table('quiz_attempts')->where('user_id', $id)
                ->orderByDesc('started_at')->get(['id', 'academy_id', 'technology_id', 'category_id',
                    'difficulty', 'mode', 'status', 'question_count', 'correct_count', 'score_percent',
                    'started_at', 'submitted_at', 'duration_seconds']),
            'quizResponses' => DB::table('quiz_attempt_answers as qa')
                ->join('quiz_attempts as a', 'a.id', '=', 'qa.attempt_id')
                ->where('a.user_id', $id)->orderByDesc('a.started_at')->orderBy('qa.position')
                ->get(['qa.attempt_id', 'qa.position', 'qa.response_json', 'qa.is_correct',
                    'qa.marks_awarded', 'qa.time_spent_ms', 'qa.answered_at']),
            'revisionSchedule' => DB::table('learner_revision_schedules')->where('user_id', $id)
                ->orderBy('due_at')->get(['academy_id', 'lesson_id', 'stage', 'due_at',
                    'last_completed_at', 'updated_at']),
            'notificationPreferences' => DB::table('learner_notification_preferences')->where('user_id', $id)
                ->get(['academy_id', 'reminders_enabled', 'push_enabled', 'quiet_start', 'quiet_end',
                    'timezone', 'updated_at']),
            'notifications' => DB::table('learner_notifications')->where('user_id', $id)
                ->orderByDesc('created_at')->get(['id', 'academy_id', 'notification_type', 'title',
                    'message', 'payload', 'status', 'scheduled_at', 'sent_at', 'created_at']),
        ]);
    }
}
