<?php

namespace App\Http\Middleware;

use Closure;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Symfony\Component\HttpFoundation\Response;

final class AuthenticateJiaToken
{
    public function handle(Request $request, Closure $next): Response
    {
        $header = $request->header('Authorization', '');
        if (! str_starts_with($header, 'Bearer ')) {
            return response()->json(['message' => 'Unauthenticated'], 401);
        }
        $rawToken = trim(substr($header, 7));
        if ($rawToken === '' || strlen($rawToken) > 160) {
            return response()->json(['message' => 'Unauthenticated'], 401);
        }
        $token = DB::table('api_tokens')->join('users', 'users.id', '=', 'api_tokens.user_id')
            ->where('api_tokens.token_hash', hash('sha256', $rawToken))
            ->whereNull('api_tokens.revoked_at')->where('api_tokens.expires_at', '>', now())
            ->where('users.status', 'ACTIVE')
            ->select('api_tokens.id as token_id', 'users.id', 'users.name', 'users.email')->first();
        if ($token === null) {
            return response()->json(['message' => 'Unauthenticated'], 401);
        }
        $roles = DB::table('roles')->join('user_roles', 'user_roles.role_id', '=', 'roles.id')
            ->where('user_roles.user_id', $token->id)->pluck('roles.code')->all();
        $permissions = DB::table('permissions')->join('role_permissions', 'role_permissions.permission_id', '=', 'permissions.id')
            ->join('user_roles', 'user_roles.role_id', '=', 'role_permissions.role_id')
            ->where('user_roles.user_id', $token->id)->distinct()->pluck('permissions.code')->all();
        $request->attributes->set('jia_user', (object) [
            'id' => $token->id, 'name' => $token->name, 'email' => $token->email,
            'roles' => $roles, 'permissions' => $permissions,
        ]);
        $request->attributes->set('jia_token_id', $token->token_id);
        DB::table('api_tokens')->where('id', $token->token_id)->update(['last_used_at' => now()]);
        return $next($request);
    }
}
