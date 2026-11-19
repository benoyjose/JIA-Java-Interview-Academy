<?php

namespace Tests\Feature;

use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;
use Illuminate\Database\Schema\Blueprint;
use Tests\TestCase;

final class ExpiredTokenAuthenticationTest extends TestCase
{
    public function test_expired_persisted_bearer_token_returns_contract_401_without_updating_last_used(): void
    {
        Schema::dropIfExists('api_tokens');
        Schema::dropIfExists('users');

        Schema::create('users', function (Blueprint $table): void {
            $table->uuid('id')->primary();
            $table->string('name', 160);
            $table->string('email', 254);
            $table->string('password_hash', 255);
            $table->string('status', 20)->default('ACTIVE');
            $table->timestampsTz();
        });

        Schema::create('api_tokens', function (Blueprint $table): void {
            $table->uuid('id')->primary();
            $table->uuid('user_id');
            $table->char('token_hash', 64)->unique();
            $table->string('token_name', 100);
            $table->timestampTz('created_at')->useCurrent();
            $table->timestampTz('last_used_at')->nullable();
            $table->timestampTz('expires_at');
            $table->timestampTz('revoked_at')->nullable();
        });

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
