<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('academies', function (Blueprint $table): void {
            $table->uuid('id')->primary();
            $table->string('code', 12)->unique();
            $table->string('name', 120);
            $table->string('description', 500)->nullable();
            $table->string('status', 20)->default('ACTIVE');
            $table->jsonb('branding_json')->default('{}');
            $table->timestampsTz();
        });
        DB::statement("ALTER TABLE academies ADD CONSTRAINT chk_academies_status CHECK (status IN ('ACTIVE','INACTIVE','DRAFT'))");
        DB::table('academies')->insert([
            'id' => '50000000-0000-0000-0000-000000000001',
            'code' => 'JIA',
            'name' => 'Java Interview Academy',
            'description' => 'Java learning and interview preparation academy.',
            'status' => 'ACTIVE',
            'branding_json' => '{}',
            'created_at' => now(),
            'updated_at' => now(),
        ]);

        Schema::table('technologies', function (Blueprint $table): void {
            $table->uuid('academy_id')->nullable();
        });
        DB::table('technologies')->update(['academy_id' => '50000000-0000-0000-0000-000000000001']);
        Schema::table('technologies', function (Blueprint $table): void {
            $table->uuid('academy_id')->nullable(false)->change();
            $table->dropUnique(['slug']);
            $table->unique(['academy_id', 'slug'], 'uq_technologies_academy_slug');
            $table->foreign('academy_id')->references('id')->on('academies');
            $table->index(['academy_id', 'status', 'sort_order'], 'idx_technologies_academy_status_order');
        });
    }

    public function down(): void
    {
        Schema::table('technologies', function (Blueprint $table): void {
            $table->dropIndex('idx_technologies_academy_status_order');
            $table->dropForeign(['academy_id']);
            $table->dropUnique('uq_technologies_academy_slug');
            $table->unique('slug');
            $table->dropColumn('academy_id');
        });
        Schema::dropIfExists('academies');
    }
};
