<?php

namespace App\Http\Controllers\Api\V1;

use App\Services\LearnerSyncBatch;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

final class LearnerSyncController
{
    public function __construct(private readonly LearnerSyncBatch $batch) {}

    public function ingest(Request $request): JsonResponse
    {
        $user = $request->attributes->get('jia_user');
        abort_if($user === null, 401, 'Unauthenticated');
        $validated = $request->validate([
            'events' => ['required', 'array', 'min:1', 'max:100'],
            'events.*' => ['required', 'array'],
        ]);
        return response()->json($this->batch->apply((string) $user->id, $validated['events']));
    }
}
