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

    public static function codeForStatus(int $status): string
    {
        return match ($status) {
            400 => 'BAD_REQUEST',
            401 => 'UNAUTHENTICATED',
            403 => 'FORBIDDEN',
            404 => 'NOT_FOUND',
            409 => 'CONFLICT',
            422 => 'VALIDATION_ERROR',
            429 => 'RATE_LIMITED',
            default => $status >= 500 ? 'INTERNAL_ERROR' : 'HTTP_ERROR',
        };
    }

    public static function forStatus(int $status, ?string $message = null): JsonResponse
    {
        $code = self::codeForStatus($status);
        $safeMessage = match ($code) {
            'BAD_REQUEST' => $message ?? 'The request is invalid.',
            'UNAUTHENTICATED' => 'Authentication is required.',
            'FORBIDDEN' => 'You do not have permission to perform this action.',
            'NOT_FOUND' => 'The requested resource was not found.',
            'CONFLICT' => $message ?? 'The request conflicts with the current resource state.',
            'VALIDATION_ERROR' => $message ?? 'The request contains invalid fields.',
            'RATE_LIMITED' => 'Too many requests. Please try again later.',
            'INTERNAL_ERROR' => 'An unexpected server error occurred.',
            default => $message ?? 'The request could not be completed.',
        };

        return self::make($code, $safeMessage, $status);
    }
}
