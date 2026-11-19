<?php

namespace App\Http\Controllers\Api\V1;

use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Str;
use Illuminate\Validation\ValidationException;

final class AuthController
{
    public function register(Request $request): JsonResponse
    {
        $data = $request->validate([
            'name' => ['required', 'string', 'max:160'],
            'email' => ['required', 'email', 'max:254'],
            'password' => ['required', 'string', 'min:12', 'max:128'],
        ]);
        $email = mb_strtolower(trim($data['email']));
        if (DB::table('users')->whereRaw('lower(email) = ?', [$email])->exists()) {
            return response()->json(['message' => 'An account with this email already exists'], 409);
        }
        $userId = (string) Str::uuid();
        DB::table('users')->insert([
            'id' => $userId, 'name' => trim($data['name']), 'email' => $email,
            'password_hash' => Hash::driver('bcrypt')->make($data['password']),
            'status' => 'ACTIVE', 'created_at' => now(), 'updated_at' => now(),
        ]);
        $learnerRoleId = DB::table('roles')->where('code', 'LEARNER')->value('id');
        DB::table('user_roles')->insert(['user_id' => $userId, 'role_id' => $learnerRoleId]);
        return response()->json($this->issueToken($userId, trim($data['name']), $email, ['LEARNER'], 'registration'), 201);
    }

    public function login(Request $request): JsonResponse
    {
        $data = $request->validate([
            'email' => ['required', 'email', 'max:254'],
            'password' => ['required', 'string', 'max:128'],
            'deviceName' => ['nullable', 'string', 'max:100'],
        ]);
        $user = DB::table('users')->whereRaw('lower(email) = ?', [mb_strtolower(trim($data['email']))])->first();
        if ($user === null || $user->status !== 'ACTIVE'
            || ! Hash::driver('bcrypt')->check($data['password'], $user->password_hash)) {
            return response()->json(['message' => 'Invalid email or password'], 401);
        }
        $roles = DB::table('roles')->join('user_roles', 'user_roles.role_id', '=', 'roles.id')
            ->where('user_roles.user_id', $user->id)->orderBy('roles.code')->pluck('roles.code')->all();
        return response()->json($this->issueToken(
            $user->id, $user->name, $user->email, $roles, $data['deviceName'] ?? 'mobile'
        ));
    }

    public function me(Request $request): JsonResponse
    {
        $user = $request->attributes->get('jia_user');
        return response()->json(['id' => $user->id, 'name' => $user->name, 'email' => $user->email, 'roles' => $user->roles]);
    }

    public function logout(Request $request): JsonResponse
    {
        DB::table('api_tokens')->where('id', $request->attributes->get('jia_token_id'))
            ->whereNull('revoked_at')->update(['revoked_at' => now()]);
        return response()->json(null, 204);
    }

    private function issueToken(string $userId, string $name, string $email, array $roles, string $device): array
    {
        $rawToken = 'jia_'.rtrim(strtr(base64_encode(random_bytes(32)), '+/', '-_'), '=');
        $expiresAt = now()->addDays(30);
        DB::table('api_tokens')->insert([
            'id' => (string) Str::uuid(), 'user_id' => $userId,
            'token_hash' => hash('sha256', $rawToken),
            'token_name' => mb_substr(trim($device) ?: 'mobile', 0, 100),
            'created_at' => now(), 'expires_at' => $expiresAt,
        ]);
        return [
            'accessToken' => $rawToken, 'tokenType' => 'Bearer', 'expiresAt' => $expiresAt->toISOString(),
            'user' => ['id' => $userId, 'name' => $name, 'email' => $email, 'roles' => $roles],
        ];
    }
}
