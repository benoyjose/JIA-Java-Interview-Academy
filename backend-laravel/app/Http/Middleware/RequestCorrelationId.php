<?php

namespace App\Http\Middleware;

use Closure;
use Illuminate\Http\Request;
use Illuminate\Support\Str;
use Illuminate\Support\Facades\Log;
use Symfony\Component\HttpFoundation\Response;
use Throwable;

final class RequestCorrelationId
{
    private const HEADER = 'X-Request-Id';

    public function handle(Request $request, Closure $next): Response
    {
        $candidate = $request->header(self::HEADER);
        $requestId = is_string($candidate)
            && preg_match('/^[A-Za-z0-9._-]{1,64}$/D', $candidate) === 1
                ? $candidate
                : (string) Str::uuid();

        $request->attributes->set('request_id', $requestId);
        $startedAt = hrtime(true);
        $status = 500;

        try {
            $response = $next($request);
            $status = $response->getStatusCode();
            $response->headers->set(self::HEADER, $requestId);
            return $response;
        } catch (Throwable $exception) {
            $status = method_exists($exception, 'getStatusCode')
                ? (int) $exception->getStatusCode()
                : 500;
            throw $exception;
        } finally {
            Log::info('api.request.completed', [
                'request_id' => $requestId,
                'method' => $request->method(),
                'route' => $request->route()?->uri() ?? 'unmatched',
                'status' => $status,
                'duration_ms' => round((hrtime(true) - $startedAt) / 1_000_000, 2),
            ]);
        }
    }
}
