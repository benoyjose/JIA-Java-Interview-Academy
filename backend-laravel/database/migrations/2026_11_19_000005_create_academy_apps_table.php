<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('academy_apps', function (Blueprint $table): void {
            $table->uuid('id')->primary();
            $table->foreignUuid('academy_id')->constrained('academies');
            $table->string('app_code', 40)->unique();
            $table->string('platform', 12);
            $table->string('application_id', 255);
            $table->string('display_name', 160);
            $table->string('status', 20)->default('PLANNED');
            $table->string('minimum_supported_version', 40)->nullable();
            $table->string('current_version', 40)->nullable();
            $table->string('store_url', 1000)->nullable();
            $table->timestampsTz();
            $table->unique(['platform', 'application_id'], 'uq_academy_app_platform_identifier');
            $table->index(['academy_id', 'status'], 'idx_academy_apps_academy_status');
        });
        DB::statement("ALTER TABLE academy_apps ADD CONSTRAINT chk_academy_app_platform CHECK (platform IN ('ANDROID','IOS'))");
        DB::statement("ALTER TABLE academy_apps ADD CONSTRAINT chk_academy_app_status CHECK (status IN ('PLANNED','ACTIVE','SUSPENDED','RETIRED'))");
    }

    public function down(): void
    {
        Schema::dropIfExists('academy_apps');
    }
};
