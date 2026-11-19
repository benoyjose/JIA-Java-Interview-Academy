<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        DB::statement('ALTER TABLE users ALTER COLUMN email DROP NOT NULL');
        DB::statement('ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL');

        Schema::create('auth_identities', function (Blueprint $table): void {
            $table->uuid('id')->primary();
            $table->foreignUuid('user_id')->constrained('users')->cascadeOnDelete();
            $table->string('provider', 20);
            $table->string('provider_subject', 255);
            $table->string('provider_email', 254)->nullable();
            $table->boolean('email_verified')->default(false);
            $table->timestampTz('created_at')->useCurrent();
            $table->timestampTz('last_authenticated_at')->nullable();
            $table->unique(['provider', 'provider_subject'], 'uq_auth_identity_provider_subject');
            $table->index('user_id', 'idx_auth_identities_user');
        });
        DB::statement("ALTER TABLE auth_identities ADD CONSTRAINT chk_auth_identity_provider CHECK (provider IN ('GOOGLE','APPLE','PHONE','EMAIL'))");

        Schema::create('phone_otp_challenges', function (Blueprint $table): void {
            $table->uuid('id')->primary();
            $table->string('phone_e164', 16);
            $table->char('code_digest', 64);
            $table->timestampTz('expires_at');
            $table->unsignedSmallInteger('attempts')->default(0);
            $table->timestampTz('consumed_at')->nullable();
            $table->timestampTz('created_at')->useCurrent();
            $table->index(['phone_e164', 'created_at'], 'idx_phone_otp_lookup');
        });
        DB::statement('ALTER TABLE phone_otp_challenges ADD CONSTRAINT chk_phone_otp_attempts CHECK (attempts <= 10)');
    }

    public function down(): void
    {
        Schema::dropIfExists('phone_otp_challenges');
        Schema::dropIfExists('auth_identities');
        DB::statement('ALTER TABLE users ALTER COLUMN email SET NOT NULL');
        DB::statement('ALTER TABLE users ALTER COLUMN password_hash SET NOT NULL');
    }
};
