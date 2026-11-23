<?php

namespace App\Http\Middleware;

use Closure;
use Illuminate\Http\Request;
use Illuminate\Support\Str;
use Symfony\Component\HttpFoundation\Response;

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
        $response = $next($request);
        $response->headers->set(self::HEADER, $requestId);

        return $response;
    }
}
