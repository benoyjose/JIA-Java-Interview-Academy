<?php

namespace App\Http\Controllers\Api\V1;

use App\Services\SmsGateway;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Str;

final class PhoneOtpController
{
    public function __construct(private readonly SmsGateway $sms) {}

    public function request(Request $request): JsonResponse
    {
        $data = $request->validate(['phoneNumber' => ['required', 'regex:/^\+[1-9][0-9]{7,14}$/']]);
        $phone = $data['phoneNumber'];
        $secret = (string) config('services.jia.otp_hmac_secret');
        if (strlen($secret) < 32) {
            abort(503, 'Phone sign-in is not configured on this server');
        }

        $recent = DB::table('phone_otp_challenges')->where('phone_e164', $phone)
            ->where('created_at', '>=', now()->subHour())->count();
        if ($recent >= 5) {
            return response()->json(['message' => 'Too many code requests. Try again later.'], 429);
        }

        $code = (string) random_int(100000, 999999);
        $id = (string) Str::uuid();
        DB::table('phone_otp_challenges')->insert([
            'id' => $id, 'phone_e164' => $phone,
            'code_digest' => hash_hmac('sha256', $id.'|'.$phone.'|'.$code, $secret),
            'expires_at' => now()->addMinutes(5),
            'attempts' => 0, 'created_at' => now(),
        ]);
        try {
            $this->sms->send($phone, 'Your JIA verification code is '.$code.'. It expires in 5 minutes.');
        } catch (\Throwable $exception) {
            DB::table('phone_otp_challenges')->where('id', $id)->delete();
            throw $exception;
        }

        return response()->json(['message' => 'If the number can receive SMS, a verification code has been sent.'], 202);
    }

    public function verify(Request $request): JsonResponse
    {
        $data = $request->validate([
            'phoneNumber' => ['required', 'regex:/^\+[1-9][0-9]{7,14}$/'],
            'code' => ['required', 'digits_between:6,6'],
            'deviceName' => ['nullable', 'string', 'max:100'],
        ]);
        $secret = (string) config('services.jia.otp_hmac_secret');
        if (strlen($secret) < 32) {
            abort(503, 'Phone sign-in is not configured on this server');
        }

        return DB::transaction(function () use ($data, $secret): JsonResponse {
            $challenge = DB::table('phone_otp_challenges')->where('phone_e164', $data['phoneNumber'])
                ->whereNull('consumed_at')->where('expires_at', '>', now())
                ->orderByDesc('created_at')->lockForUpdate()->first();
            if ($challenge === null || $challenge->attempts >= 5) {
                return response()->json(['message' => 'Invalid or expired verification code'], 401);
            }
            DB::table('phone_otp_challenges')->where('id', $challenge->id)->increment('attempts');
            $expected = hash_hmac('sha256', $challenge->id.'|'.$challenge->phone_e164.'|'.$data['code'], $secret);
            if (! hash_equals($challenge->code_digest, $expected)) {
                return response()->json(['message' => 'Invalid or expired verification code'], 401);
            }
            DB::table('phone_otp_challenges')->where('id', $challenge->id)->update(['consumed_at' => now()]);

            $subject = $data['phoneNumber'];
            $identity = DB::table('auth_identities')->where('provider', 'PHONE')
                ->where('provider_subject', $subject)->first();
            if ($identity) {
                $user = DB::table('users')->where('id', $identity->user_id)->first();
                if (! $user || $user->status !== 'ACTIVE') {
                    abort(403, 'Account is not active');
                }
                DB::table('auth_identities')->where('id', $identity->id)->update(['last_authenticated_at' => now()]);
            } else {
                $userId = (string) Str::uuid();
                DB::table('users')->insert([
                    'id' => $userId, 'name' => 'JIA Learner', 'email' => null,
                    'password_hash' => null, 'status' => 'ACTIVE',
                    'created_at' => now(), 'updated_at' => now(),
                ]);
                $roleId = DB::table('roles')->where('code', 'LEARNER')->value('id');
                DB::table('user_roles')->insert(['user_id' => $userId, 'role_id' => $roleId]);
                DB::table('auth_identities')->insert([
                    'id' => (string) Str::uuid(), 'user_id' => $userId,
                    'provider' => 'PHONE', 'provider_subject' => $subject,
                    'provider_email' => null, 'email_verified' => false,
                    'created_at' => now(), 'last_authenticated_at' => now(),
                ]);
                $user = DB::table('users')->where('id', $userId)->first();
            }

            $roles = DB::table('roles')->join('user_roles', 'user_roles.role_id', '=', 'roles.id')
                ->where('user_roles.user_id', $user->id)->orderBy('roles.code')->pluck('roles.code')->all();
            $token = 'jia_'.rtrim(strtr(base64_encode(random_bytes(32)), '+/', '-_'), '=');
            $expires = now()->addDays(30);
            DB::table('api_tokens')->insert([
                'id' => (string) Str::uuid(), 'user_id' => $user->id,
                'token_hash' => hash('sha256', $token),
                'token_name' => mb_substr(trim($data['deviceName'] ?? 'mobile') ?: 'mobile', 0, 100),
                'created_at' => now(), 'expires_at' => $expires,
            ]);
            return response()->json([
                'accessToken' => $token, 'tokenType' => 'Bearer', 'expiresAt' => $expires->toISOString(),
                'user' => ['id' => $user->id, 'name' => $user->name, 'email' => $user->email, 'roles' => $roles],
            ]);
        });
    }
}
