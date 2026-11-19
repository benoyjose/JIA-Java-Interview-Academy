<?php

namespace App\Services;

use Firebase\JWT\JWK;
use Firebase\JWT\JWT;
use Illuminate\Support\Facades\Cache;
use Illuminate\Support\Facades\Http;
use RuntimeException;

final class FederatedIdentityVerifier
{
    public function verifyGoogle(string $token, string $nonce): array
    {
        return $this->verify(
            $token,
            $nonce,
            (string) config('services.jia.google_client_id'),
            ['https://accounts.google.com', 'accounts.google.com'],
            'https://www.googleapis.com/oauth2/v3/certs',
            false
        );
    }

    public function verifyApple(string $token, string $nonce): array
    {
        return $this->verify(
            $token,
            $nonce,
            (string) config('services.jia.apple_client_id'),
            ['https://appleid.apple.com'],
            'https://appleid.apple.com/auth/keys',
            true
        );
    }

    private function verify(string $token, string $nonce, string $audience, array $issuers,
                            string $jwksUrl, bool $hashNonce): array
    {
        if ($audience === '') {
            abort(503, 'Federated sign-in is not configured on this server');
        }
        if ($token === '' || strlen($token) > 12000 || $nonce === '') {
            abort(401, 'Invalid federated identity token');
        }

        try {
            $keySet = Cache::remember('jia.jwks.'.sha1($jwksUrl), 3600, function () use ($jwksUrl): array {
                $response = Http::timeout(5)->retry(2, 200)->get($jwksUrl);
                if (! $response->successful()) {
                    throw new RuntimeException('Provider key endpoint unavailable');
                }
                return $response->json();
            });
            $keys = JWK::parseKeySet($keySet, 'RS256');
            $claims = JWT::decode($token, $keys);
        } catch (\Throwable $exception) {
            abort(401, 'Invalid federated identity token');
        }

        $issuer = (string) ($claims->iss ?? '');
        $audiences = is_array($claims->aud ?? null) ? $claims->aud : [($claims->aud ?? null)];
        $expectedNonce = $hashNonce ? hash('sha256', $nonce) : $nonce;
        $expiry = (int) ($claims->exp ?? 0);
        if (! in_array($issuer, $issuers, true)
            || ! in_array($audience, $audiences, true)
            || $expiry <= time()
            || ! hash_equals($expectedNonce, (string) ($claims->nonce ?? ''))
            || empty($claims->sub)) {
            abort(401, 'Invalid federated identity token');
        }

        return (array) $claims;
    }
}
