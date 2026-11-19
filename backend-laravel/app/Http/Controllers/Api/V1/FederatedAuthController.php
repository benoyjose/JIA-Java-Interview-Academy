<?php

namespace App\Http\Controllers\Api\V1;

use App\Services\FederatedIdentityVerifier;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;

final class FederatedAuthController
{
    public function __construct(private readonly FederatedIdentityVerifier $verifier) {}

    public function google(Request $request): JsonResponse
    {
        $data = $request->validate([
            'idToken' => ['required', 'string', 'max:12000'],
            'nonce' => ['required', 'string', 'max:256'],
            'deviceName' => ['nullable', 'string', 'max:100'],
        ]);
        $claims = $this->verifier->verifyGoogle($data['idToken'], $data['nonce']);
        $email = isset($claims['email']) ? mb_strtolower(trim((string) $claims['email'])) : null;
        $verified = filter_var($claims['email_verified'] ?? false, FILTER_VALIDATE_BOOLEAN);
        if ($email === null || ! $verified) {
            return response()->json(['message' => 'Invalid federated identity token'], 401);
        }
        return response()->json($this->resolveIdentity(
            'GOOGLE', (string) $claims['sub'], $email, true,
            isset($claims['name']) ? (string) $claims['name'] : null,
            $data['deviceName'] ?? 'mobile'
        ));
    }

    public function apple(Request $request): JsonResponse
    {
        $data = $request->validate([
            'identityToken' => ['required', 'string', 'max:12000'],
            'nonce' => ['required', 'string', 'max:256'],
            'deviceName' => ['nullable', 'string', 'max:100'],
        ]);
        $claims = $this->verifier->verifyApple($data['identityToken'], $data['nonce']);
        $email = isset($claims['email']) ? mb_strtolower(trim((string) $claims['email'])) : null;
        $verified = filter_var($claims['email_verified'] ?? false, FILTER_VALIDATE_BOOLEAN);
        return response()->json($this->resolveIdentity(
            'APPLE', (string) $claims['sub'], $email, $verified, null,
            $data['deviceName'] ?? 'mobile'
        ));
    }

    private function resolveIdentity(string $provider, string $subject, ?string $email,
                                     bool $emailVerified, ?string $displayName, string $device): array
    {
        return DB::transaction(function () use ($provider, $subject, $email, $emailVerified, $displayName, $device): array {
            $identity = DB::table('auth_identities')
                ->where('provider', $provider)->where('provider_subject', $subject)->first();

            if ($identity !== null) {
                $user = DB::table('users')->where('id', $identity->user_id)->first();
                if ($user === null || $user->status !== 'ACTIVE') {
                    abort(403, 'Account is not active');
                }
                DB::table('auth_identities')->where('id', $identity->id)
                    ->update(['last_authenticated_at' => now()]);
            } else {
                if ($email !== null) {
                    if (! $emailVerified) {
                        abort(401, 'Provider email is not verified');
                    }
                    if (DB::table('users')->whereRaw('lower(email) = ?', [$email])->exists()) {
                        abort(409, 'An account already uses this email. Sign in using that method and link the provider from account settings.');
                    }
                }

                $userId = (string) Str::uuid();
                $name = trim((string) $displayName);
                $name = $name === '' ? 'JIA Learner' : mb_substr($name, 0, 160);
                DB::table('users')->insert([
                    'id' => $userId, 'name' => $name, 'email' => $email,
                    'password_hash' => null, 'status' => 'ACTIVE',
                    'created_at' => now(), 'updated_at' => now(),
                ]);
                $learnerRoleId = DB::table('roles')->where('code', 'LEARNER')->value('id');
                DB::table('user_roles')->insert(['user_id' => $userId, 'role_id' => $learnerRoleId]);
                DB::table('auth_identities')->insert([
                    'id' => (string) Str::uuid(), 'user_id' => $userId,
                    'provider' => $provider, 'provider_subject' => $subject,
                    'provider_email' => $email, 'email_verified' => $emailVerified,
                    'created_at' => now(), 'last_authenticated_at' => now(),
                ]);
                $user = DB::table('users')->where('id', $userId)->first();
            }

            $roles = DB::table('roles')->join('user_roles', 'user_roles.role_id', '=', 'roles.id')
                ->where('user_roles.user_id', $user->id)->orderBy('roles.code')->pluck('roles.code')->all();
            $rawToken = 'jia_'.rtrim(strtr(base64_encode(random_bytes(32)), '+/', '-_'), '=');
            $expiresAt = now()->addDays(30);
            DB::table('api_tokens')->insert([
                'id' => (string) Str::uuid(), 'user_id' => $user->id,
                'token_hash' => hash('sha256', $rawToken),
                'token_name' => mb_substr(trim($device) ?: 'mobile', 0, 100),
                'created_at' => now(), 'expires_at' => $expiresAt,
            ]);

            return [
                'accessToken' => $rawToken,
                'tokenType' => 'Bearer',
                'expiresAt' => $expiresAt->toISOString(),
                'user' => ['id' => $user->id, 'name' => $user->name, 'email' => $user->email, 'roles' => $roles],
            ];
        });
    }
}
