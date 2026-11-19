<?php

namespace App\Services;

use Illuminate\Support\Facades\Http;
use RuntimeException;

final class SmsGateway
{
    public function send(string $phoneE164, string $message): void
    {
        $endpoint = (string) config('services.jia.sms_endpoint');
        $token = (string) config('services.jia.sms_token');
        if ($endpoint === '' || $token === '') {
            abort(503, 'Phone sign-in is not configured on this server');
        }

        $response = Http::withToken($token)->acceptJson()->timeout(8)->post($endpoint, [
            'to' => $phoneE164,
            'message' => $message,
        ]);
        if (! $response->successful()) {
            throw new RuntimeException('SMS gateway rejected the delivery request');
        }
    }
}
