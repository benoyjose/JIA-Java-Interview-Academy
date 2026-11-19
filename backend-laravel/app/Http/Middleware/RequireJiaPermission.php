<?php

namespace App\Http\Middleware;

use App\Support\ApiErrorResponse;
use Closure;
use Illuminate\Http\Request;
use Symfony\Component\HttpFoundation\Response;

final class RequireJiaPermission
{
    public function handle(Request $request, Closure $next, string $permission): Response
    {
        $user = $request->attributes->get('jia_user');
        if ($user === null || ! in_array($permission, $user->permissions, true)) {
            return ApiErrorResponse::forStatus(403);
        }
        return $next($request);
    }
}
