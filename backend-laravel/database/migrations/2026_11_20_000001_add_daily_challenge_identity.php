<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        DB::statement('ALTER TABLE quiz_attempts ADD COLUMN challenge_date DATE');
        DB::statement("CREATE UNIQUE INDEX uq_quiz_attempts_daily_challenge
            ON quiz_attempts(user_id, academy_id, challenge_date)
            WHERE mode = 'DAILY_CHALLENGE' AND challenge_date IS NOT NULL");
        DB::statement("CREATE INDEX idx_quiz_attempts_daily_challenge_date
            ON quiz_attempts(challenge_date) WHERE mode = 'DAILY_CHALLENGE'");
    }

    public function down(): void
    {
        DB::statement('DROP INDEX IF EXISTS idx_quiz_attempts_daily_challenge_date');
        DB::statement('DROP INDEX IF EXISTS uq_quiz_attempts_daily_challenge');
        DB::statement('ALTER TABLE quiz_attempts DROP COLUMN challenge_date');
    }
};
