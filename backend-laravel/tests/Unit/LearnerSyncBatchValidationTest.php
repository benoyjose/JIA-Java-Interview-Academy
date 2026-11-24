<?php

namespace Tests\Unit;

use App\Services\LearnerSyncBatch;
use Symfony\Component\HttpKernel\Exception\ConflictHttpException;
use Symfony\Component\HttpKernel\Exception\UnprocessableEntityHttpException;
use Tests\TestCase;

final class LearnerSyncBatchValidationTest extends TestCase
{
    public function test_batch_is_bounded_before_any_database_write(): void
    {
        $this->expectException(UnprocessableEntityHttpException::class);
        (new LearnerSyncBatch())->apply('60000000-0000-0000-0000-000000000001', array_fill(0, 101, []));
    }

    public function test_same_batch_uuid_cannot_represent_two_different_snapshots(): void
    {
        $eventId = '7a000000-0000-0000-0000-000000000001';
        $first = ['eventId' => $eventId, 'kind' => 'PROGRESS', 'lessonSlug' => 'java-streams',
            'updatedAt' => 100, 'contentRevision' => 1, 'progressPercent' => 20];
        $second = [...$first, 'progressPercent' => 80];
        $this->expectException(ConflictHttpException::class);
        (new LearnerSyncBatch())->apply('60000000-0000-0000-0000-000000000001', [$first, $second]);
    }

    public function test_invalid_kind_and_missing_identity_are_rejected_without_writes(): void
    {
        $this->expectException(UnprocessableEntityHttpException::class);
        (new LearnerSyncBatch())->apply('60000000-0000-0000-0000-000000000001', [
            ['eventId' => '7a000000-0000-0000-0000-000000000001', 'kind' => 'CLIENT_MASTERY',
                'lessonSlug' => 'java-streams', 'updatedAt' => 1],
        ]);
    }
}
