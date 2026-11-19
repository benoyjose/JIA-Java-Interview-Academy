<?php

namespace App\Http\Controllers\Api\V1;

use App\Services\QuestionImportService;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;

final class QuestionImportController
{
    public function __construct(private readonly QuestionImportService $imports) {}

    public function import(Request $request): JsonResponse
    {
        $actor = $request->attributes->get('jia_user');
        abort_if($actor === null, 401, 'Unauthenticated');
        return response()->json($this->imports->import($request->all(), $actor), 201);
    }
}
