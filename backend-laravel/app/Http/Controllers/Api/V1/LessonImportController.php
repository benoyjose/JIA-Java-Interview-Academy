<?php

namespace App\Http\Controllers\Api\V1;

use App\Services\LessonImportService;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

final class LessonImportController
{
    public function __construct(private readonly LessonImportService $imports) {}

    public function import(Request $request): JsonResponse
    {
        $actor = $request->attributes->get('jia_user');
        abort_if($actor === null, 401, 'Unauthenticated');
        $result = $this->imports->import($request->all(), $actor);

        return response()->json($result, 201);
    }
}
