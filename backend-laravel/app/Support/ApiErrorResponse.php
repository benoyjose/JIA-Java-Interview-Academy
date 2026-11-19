<?php

namespace App\Support;

use Illuminate\Http\JsonResponse;

final class ApiErrorResponse
{
    public static function make(
        string $code,
        string $message,
        int $status,
        ?array $errors = null,
    ): JsonResponse {
        $body = ['code' => $code, 'message' => $message];
        if ($errors !== null && $errors !== []) {
            $body['errors'] = $errors;
        }

        return response()->json($body, $status);
    }

    public static function forStatus(int $status, ?string $message = null): JsonResponse
    {
        [$code, $safeMessage] = match ($status) {
            400 => ['BAD_REQUEST', $message ?? 'The request is invalid.'],
            401 => ['UNAUTHENTICATED', 'Authentication is required.'],
            403 => ['FORBIDDEN', 'You do not have permission to perform this action.'],
            404 => ['NOT_FOUND', 'The requested resource was not found.'],
            409 => ['CONFLICT', $message ?? 'The request conflicts with the current resource state.'],
            422 => ['VALIDATION_ERROR', $message ?? 'The request contains invalid fields.'],
            429 => ['RATE_LIMITED', 'Too many requests. Please try again later.'],
            default => $status >= 500
                ? ['INTERNAL_ERROR', 'An unexpected server error occurred.']
                : ['HTTP_ERROR', $message ?? 'The request could not be completed.'],
        };

        return self::make($code, $safeMessage, $status);
    }
}
