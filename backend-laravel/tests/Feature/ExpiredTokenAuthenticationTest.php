<?php

namespace Tests\Feature;

use Illuminate\Support\Facades\Artisan;
use Illuminate\Support\Facades\DB;
use Tests\TestCase;

final class ExpiredTokenAuthenticationTest extends TestCase
{
    public function test_expired_persisted_bearer_token_returns_contract_401_without_updating_last_used(): void
    {
        Artisan::call('migrate:fresh', ['--force' => true]);

        $userId = '60000000-0000-0000-0000-000000000099';
        $tokenId = '72000000-0000-0000-0000-000000000099';
        $rawToken = 'jia-expired-token-integration-test';

        DB::table('users')->insert([
            'id' => $userId,
            'name' => 'Expired Token User',
            'email' => 'expired-token@example.test',
            'password_hash' => 'test-only-hash',
            'status' => 'ACTIVE',
            'created_at' => now(),
            'updated_at' => now(),
        ]);

        DB::table('api_tokens')->insert([
            'id' => $tokenId,
            'user_id' => $userId,
            'token_hash' => hash('sha256', $rawToken),
            'token_name' => 'expired-integration-test',
            'created_at' => now()->subDay(),
            'expires_at' => now()->subMinute(),
            'revoked_at' => null,
            'last_used_at' => null,
        ]);

        $this->withHeader('Authorization', 'Bearer '.$rawToken)
            ->getJson('/api/v1/auth/me')
            ->assertUnauthorized()
            ->assertJsonPath('code', 'UNAUTHENTICATED')
            ->assertJsonPath('message', 'Authentication is required.');

        $this->assertDatabaseHas('api_tokens', [
            'id' => $tokenId,
            'last_used_at' => null,
        ]);
    }
}
