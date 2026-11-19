<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::table('quiz_attempts', function (Blueprint $table): void {
            $table->unsignedInteger('duration_limit_seconds')->nullable();
            $table->timestampTz('deadline_at')->nullable();
        });

        DB::statement("ALTER TABLE quiz_attempts ADD CONSTRAINT chk_quiz_attempt_duration_limit CHECK (duration_limit_seconds IS NULL OR duration_limit_seconds BETWEEN 60 AND 21600)");
        DB::statement("CREATE INDEX idx_quiz_attempts_active_deadline ON quiz_attempts(deadline_at) WHERE status = 'IN_PROGRESS' AND deadline_at IS NOT NULL");
    }

    public function down(): void
    {
        DB::statement('DROP INDEX IF EXISTS idx_quiz_attempts_active_deadline');
        DB::statement('ALTER TABLE quiz_attempts DROP CONSTRAINT IF EXISTS chk_quiz_attempt_duration_limit');
        Schema::table('quiz_attempts', function (Blueprint $table): void {
            $table->dropColumn(['duration_limit_seconds', 'deadline_at']);
        });
    }
};
