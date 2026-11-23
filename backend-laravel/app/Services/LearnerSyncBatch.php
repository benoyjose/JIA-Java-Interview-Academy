<?php

namespace App\Services;

use Illuminate\Support\Facades\DB;
use Symfony\Component\HttpKernel\Exception\ConflictHttpException;
use Symfony\Component\HttpKernel\Exception\UnprocessableEntityHttpException;

/**
 * Atomic, idempotent receipt-driven reconciliation for offline learner snapshots.
 * Client state is never allowed to decrease server progress or overwrite a newer position.
 */
final class LearnerSyncBatch
{
    public function apply(string $userId, array $events): array
    {
        if ($events === [] || count($events) > 100) {
            throw new UnprocessableEntityHttpException('Sync batch must contain 1–100 events.');
        }

        $seen = [];
        foreach ($events as $event) {
            if (!is_array($event) || !isset($event['eventId'], $event['kind'], $event['lessonSlug'], $event['updatedAt'])) {
                throw new UnprocessableEntityHttpException('Each sync event requires eventId, kind, lessonSlug and updatedAt.');
            }
            if (!in_array($event['kind'], ['PROGRESS', 'BOOKMARK'], true)) {
                throw new UnprocessableEntityHttpException('Unsupported offline event kind.');
            }
            if (!is_string($event['eventId']) || !preg_match('/^[0-9a-f-]{36}$/i', $event['eventId'])) {
                throw new UnprocessableEntityHttpException('eventId must be a UUID.');
            }
            if (isset($seen[$event['eventId']]) && $seen[$event['eventId']] !== $event) {
                throw new ConflictHttpException('A batch reuses an event ID with different content.');
            }
            $seen[$event['eventId']] = $event;
        }

        return DB::transaction(function () use ($userId, $events): array {
            $receipts = [];
            foreach ($events as $event) {
                $canonical = json_encode($event, JSON_THROW_ON_ERROR | JSON_UNESCAPED_SLASHES);
                $hash = hash('sha256', $canonical);
                $inserted = DB::table('learner_sync_receipts')->insertOrIgnore([
                    'user_id' => $userId, 'event_id' => $event['eventId'],
                    'event_hash' => $hash, 'created_at' => now(),
                ]);
                if ($inserted === 0) {
                    $prior = DB::table('learner_sync_receipts')->where('user_id', $userId)
                        ->where('event_id', $event['eventId'])->value('event_hash');
                    if (!is_string($prior) || !hash_equals($prior, $hash)) {
                        throw new ConflictHttpException('Event ID was already accepted with a different body.');
                    }
                    $receipts[] = ['eventId' => $event['eventId'], 'status' => 'DUPLICATE'];
                    continue;
                }

                $lesson = DB::table('lessons')->where('slug', $event['lessonSlug'])
                    ->where('status', 'PUBLISHED')->first(['id', 'published_revision_id']);
                if ($lesson === null) {
                    throw new UnprocessableEntityHttpException('Offline event references a lesson that is no longer published.');
                }
                if ($event['kind'] === 'PROGRESS') {
                    $revision = filter_var($event['contentRevision'] ?? null, FILTER_VALIDATE_INT);
                    $percent = filter_var($event['progressPercent'] ?? null, FILTER_VALIDATE_FLOAT);
                    if ($revision === false || $revision < 1 || $percent === false || $percent < 0 || $percent > 100) {
                        throw new UnprocessableEntityHttpException('Progress revision or percentage is invalid.');
                    }
                    $currentRevision = DB::table('lesson_revisions')->where('id', $lesson->published_revision_id)
                        ->value('revision_number');
                    if ($currentRevision === null || $revision > (int) $currentRevision) {
                        throw new ConflictHttpException('Content revision is newer than the published lesson; refresh and retry.');
                    }
                    $position = isset($event['lastPosition']) ? json_encode($event['lastPosition'], JSON_THROW_ON_ERROR) : null;
                    DB::statement(<<<'SQL'
                        INSERT INTO learner_lesson_progress
                          (user_id,lesson_id,content_revision,status,progress_percent,last_position_json,completed_at,updated_at)
                        VALUES (?, ?, ?, ?, ?, ?::jsonb, CASE WHEN ? >= 100 THEN now() ELSE NULL END, now())
                        ON CONFLICT (user_id,lesson_id) DO UPDATE SET
                          content_revision=GREATEST(learner_lesson_progress.content_revision,EXCLUDED.content_revision),
                          progress_percent=GREATEST(learner_lesson_progress.progress_percent,EXCLUDED.progress_percent),
                          status=CASE WHEN GREATEST(learner_lesson_progress.progress_percent,EXCLUDED.progress_percent)>=100
                            THEN 'COMPLETED' ELSE learner_lesson_progress.status END,
                          last_position_json=CASE WHEN EXCLUDED.progress_percent>=learner_lesson_progress.progress_percent
                            THEN COALESCE(EXCLUDED.last_position_json,learner_lesson_progress.last_position_json)
                            ELSE learner_lesson_progress.last_position_json END,
                          completed_at=COALESCE(learner_lesson_progress.completed_at,EXCLUDED.completed_at),
                          updated_at=GREATEST(now(),learner_lesson_progress.updated_at + interval '1 microsecond')
                        SQL, [$userId, $lesson->id, $revision, $percent >= 100 ? 'COMPLETED' : 'IN_PROGRESS',
                            $percent, $position, $percent]);
                } else {
                    if (!is_bool($event['bookmarked'] ?? null)) {
                        throw new UnprocessableEntityHttpException('Bookmark event requires a boolean value.');
                    }
                    if ($event['bookmarked']) {
                        DB::table('learner_bookmarks')->insertOrIgnore([
                            'user_id' => $userId, 'lesson_id' => $lesson->id, 'created_at' => now(),
                        ]);
                    } else {
                        DB::table('learner_bookmarks')->where('user_id', $userId)
                            ->where('lesson_id', $lesson->id)->delete();
                    }
                }
                $receipts[] = ['eventId' => $event['eventId'], 'status' => 'ACCEPTED'];
            }
            return ['events' => $receipts,
                'acceptedCount' => count(array_filter($receipts, fn ($r) => $r['status'] === 'ACCEPTED')),
                'duplicateCount' => count(array_filter($receipts, fn ($r) => $r['status'] === 'DUPLICATE'))];
        }, 3);
    }
}
