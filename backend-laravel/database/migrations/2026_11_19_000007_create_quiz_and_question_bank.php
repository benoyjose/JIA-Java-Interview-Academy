<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('questions', function (Blueprint $table): void {
            $table->uuid('id')->primary();
            $table->uuid('academy_id');
            $table->uuid('technology_id');
            $table->uuid('category_id')->nullable();
            $table->uuid('lesson_id')->nullable();
            $table->string('question_type', 24);
            $table->string('difficulty', 20);
            $table->string('status', 20)->default('DRAFT');
            $table->unsignedInteger('current_revision')->default(1);
            $table->timestampsTz();
            $table->timestampsTz();
            $table->foreign('academy_id')->references('id')->on('academies');
            $table->foreign('technology_id')->references('id')->on('technologies');
            $table->foreign('category_id')->references('id')->on('categories');
            $table->foreign('lesson_id')->references('id')->on('lessons');
            $table->index(['academy_id', 'technology_id', 'category_id', 'difficulty', 'status'], 'idx_questions_quiz_filter');
        });
        DB::statement("ALTER TABLE questions ADD COLUMN tags TEXT[] NOT NULL DEFAULT '{}'");\n        DB::statement("ALTER TABLE questions ADD CONSTRAINT chk_question_type CHECK (question_type IN ('SINGLE_SELECT','MULTI_SELECT','TRUE_FALSE','OUTPUT_PREDICTION','DEBUGGING','CODING','SQL','SCENARIO'))");
        DB::statement("ALTER TABLE questions ADD CONSTRAINT chk_question_difficulty CHECK (difficulty IN ('BEGINNER','NOVICE','PROFESSIONAL','EXPERT'))");
        DB::statement("ALTER TABLE questions ADD CONSTRAINT chk_question_status CHECK (status IN ('DRAFT','IN_REVIEW','APPROVED','PUBLISHED','ARCHIVED'))");

        Schema::create('question_revisions', function (Blueprint $table): void {
            $table->uuid('id')->primary();
            $table->uuid('question_id');
            $table->unsignedInteger('revision_number');
            $table->jsonb('prompt_json');
            $table->jsonb('options_json')->nullable();
            $table->jsonb('answer_json');
            $table->text('explanation');
            $table->text('hint')->nullable();
            $table->string('source_type', 30);
            $table->text('source_description')->nullable();
            $table->text('attribution')->nullable();
            $table->uuid('reviewed_by')->nullable();
            $table->timestampTz('reviewed_at')->nullable();
            $table->uuid('created_by')->nullable();
            $table->timestampTz('created_at')->useCurrent();
            $table->foreign('question_id')->references('id')->on('questions')->cascadeOnDelete();
            $table->foreign('reviewed_by')->references('id')->on('users');
            $table->foreign('created_by')->references('id')->on('users');
            $table->unique(['question_id', 'revision_number'], 'uq_question_revision');
        });
        DB::statement("ALTER TABLE question_revisions ADD CONSTRAINT chk_question_source CHECK (source_type IN ('USER_ORIGINAL','ADMIN_AUTHORED','EXTERNAL_REFERENCE'))");

        Schema::create('quiz_attempts', function (Blueprint $table): void {
            $table->uuid('id')->primary();
            $table->uuid('user_id');
            $table->uuid('academy_id');
            $table->uuid('technology_id')->nullable();
            $table->uuid('category_id')->nullable();
            $table->string('difficulty', 20);
            $table->string('mode', 24)->default('PRACTICE');
            $table->string('status', 20)->default('IN_PROGRESS');
            $table->unsignedInteger('question_count');
            $table->unsignedInteger('correct_count')->nullable();
            $table->decimal('score_percent', 5, 2)->nullable();
            $table->timestampTz('started_at')->useCurrent();
            $table->timestampTz('submitted_at')->nullable();
            $table->unsignedInteger('duration_seconds')->nullable();
            $table->foreign('user_id')->references('id')->on('users');
            $table->foreign('academy_id')->references('id')->on('academies');
            $table->foreign('technology_id')->references('id')->on('technologies');
            $table->foreign('category_id')->references('id')->on('categories');
            $table->index(['user_id', 'academy_id', 'difficulty', 'started_at'], 'idx_quiz_attempts_user_level_date');
            $table->index(['user_id', 'technology_id', 'category_id', 'started_at'], 'idx_quiz_attempts_user_topic_date');
        });
        DB::statement("ALTER TABLE quiz_attempts ADD CONSTRAINT chk_attempt_difficulty CHECK (difficulty IN ('BEGINNER','NOVICE','PROFESSIONAL','EXPERT'))");
        DB::statement("ALTER TABLE quiz_attempts ADD CONSTRAINT chk_attempt_mode CHECK (mode IN ('PRACTICE','TIMED_MOCK','DAILY_CHALLENGE','WEAK_AREA'))");
        DB::statement("ALTER TABLE quiz_attempts ADD CONSTRAINT chk_attempt_status CHECK (status IN ('IN_PROGRESS','SUBMITTED','ABANDONED'))");
        DB::statement("ALTER TABLE quiz_attempts ADD CONSTRAINT chk_attempt_question_count CHECK (question_count BETWEEN 1 AND 100)");

        Schema::create('quiz_attempt_questions', function (Blueprint $table): void {
            $table->uuid('attempt_id');
            $table->uuid('question_id');
            $table->uuid('question_revision_id');
            $table->unsignedInteger('position');
            $table->decimal('max_marks', 6, 2)->default(1);
            $table->primary(['attempt_id', 'position']);
            $table->unique(['attempt_id', 'question_id']);
            $table->foreign('attempt_id')->references('id')->on('quiz_attempts')->cascadeOnDelete();
            $table->foreign('question_id')->references('id')->on('questions');
            $table->foreign('question_revision_id')->references('id')->on('question_revisions');
        });

        Schema::create('quiz_attempt_answers', function (Blueprint $table): void {
            $table->uuid('attempt_id');
            $table->unsignedInteger('position');
            $table->jsonb('response_json')->nullable();
            $table->boolean('is_correct')->nullable();
            $table->decimal('marks_awarded', 6, 2)->nullable();
            $table->unsignedInteger('time_spent_ms')->nullable();
            $table->timestampTz('answered_at')->nullable();
            $table->primary(['attempt_id', 'position']);
            $table->foreign(['attempt_id', 'position'], 'fk_attempt_answer_question')
                ->references(['attempt_id', 'position'])->on('quiz_attempt_questions')->cascadeOnDelete();
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('quiz_attempt_answers');
        Schema::dropIfExists('quiz_attempt_questions');
        Schema::dropIfExists('quiz_attempts');
        Schema::dropIfExists('question_revisions');
        Schema::dropIfExists('questions');
    }
};
