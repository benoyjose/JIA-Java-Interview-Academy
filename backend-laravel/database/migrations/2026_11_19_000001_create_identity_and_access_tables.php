<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('users', function (Blueprint $table): void {
            $table->uuid('id')->primary();
            $table->string('name', 160);
            $table->string('email', 254)->unique();
            $table->string('password_hash', 255);
            $table->string('status', 20)->default('ACTIVE');
            $table->timestampTz('email_verified_at')->nullable();
            $table->timestampsTz();
        });

        Schema::create('roles', function (Blueprint $table): void {
            $table->uuid('id')->primary();
            $table->string('code', 40)->unique();
            $table->string('name', 100);
        });

        Schema::create('permissions', function (Blueprint $table): void {
            $table->uuid('id')->primary();
            $table->string('code', 80)->unique();
            $table->string('description', 255)->nullable();
        });

        Schema::create('user_roles', function (Blueprint $table): void {
            $table->foreignUuid('user_id')->constrained('users')->cascadeOnDelete();
            $table->foreignUuid('role_id')->constrained('roles')->cascadeOnDelete();
            $table->timestampTz('assigned_at')->useCurrent();
            $table->primary(['user_id', 'role_id']);
            $table->index('role_id', 'idx_user_roles_role');
        });

        Schema::create('role_permissions', function (Blueprint $table): void {
            $table->foreignUuid('role_id')->constrained('roles')->cascadeOnDelete();
            $table->foreignUuid('permission_id')->constrained('permissions')->cascadeOnDelete();
            $table->primary(['role_id', 'permission_id']);
        });

        Schema::create('api_tokens', function (Blueprint $table): void {
            $table->uuid('id')->primary();
            $table->foreignUuid('user_id')->constrained('users')->cascadeOnDelete();
            $table->char('token_hash', 64)->unique();
            $table->string('token_name', 100);
            $table->timestampTz('created_at')->useCurrent();
            $table->timestampTz('last_used_at')->nullable();
            $table->timestampTz('expires_at');
            $table->timestampTz('revoked_at')->nullable();
            $table->index(['user_id', 'expires_at'], 'idx_api_tokens_user_expiry');
        });

        DB::table('roles')->insert([
            ['id' => '30000000-0000-0000-0000-000000000001', 'code' => 'LEARNER', 'name' => 'Learner'],
            ['id' => '30000000-0000-0000-0000-000000000002', 'code' => 'EDITOR', 'name' => 'Content editor'],
            ['id' => '30000000-0000-0000-0000-000000000003', 'code' => 'REVIEWER', 'name' => 'Content reviewer'],
            ['id' => '30000000-0000-0000-0000-000000000004', 'code' => 'ADMIN', 'name' => 'Administrator'],
        ]);

        DB::table('permissions')->insert([
            ['id' => '40000000-0000-0000-0000-000000000001', 'code' => 'catalogue.read', 'description' => 'Read published catalogue'],
            ['id' => '40000000-0000-0000-0000-000000000002', 'code' => 'content.create', 'description' => 'Create and edit draft content'],
            ['id' => '40000000-0000-0000-0000-000000000003', 'code' => 'content.review', 'description' => 'Review submitted content'],
            ['id' => '40000000-0000-0000-0000-000000000004', 'code' => 'content.publish', 'description' => 'Publish approved content'],
            ['id' => '40000000-0000-0000-0000-000000000005', 'code' => 'users.manage', 'description' => 'Manage user accounts and roles'],
            ['id' => '40000000-0000-0000-0000-000000000006', 'code' => 'releases.manage', 'description' => 'Manage application releases'],
        ]);

        $mapping = [
            'LEARNER' => ['catalogue.read'],
            'EDITOR' => ['catalogue.read', 'content.create'],
            'REVIEWER' => ['catalogue.read', 'content.review'],
            'ADMIN' => ['catalogue.read', 'content.create', 'content.review', 'content.publish', 'users.manage', 'releases.manage'],
        ];

        foreach ($mapping as $role => $permissions) {
            $roleId = DB::table('roles')->where('code', $role)->value('id');
            foreach ($permissions as $permission) {
                $permissionId = DB::table('permissions')->where('code', $permission)->value('id');
                DB::table('role_permissions')->insert([
                    'role_id' => $roleId,
                    'permission_id' => $permissionId,
                ]);
            }
        }
    }

    public function down(): void
    {
        Schema::dropIfExists('api_tokens');
        Schema::dropIfExists('role_permissions');
        Schema::dropIfExists('user_roles');
        Schema::dropIfExists('permissions');
        Schema::dropIfExists('roles');
        Schema::dropIfExists('users');
    }
};
