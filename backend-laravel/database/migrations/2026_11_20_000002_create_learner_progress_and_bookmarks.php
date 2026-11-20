<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('learner_lesson_progress', function (Blueprint $table): void {
            $table->foreignUuid('user_id')->constrained('users')->cascadeOnDelete();
            $table->foreignUuid('lesson_id')->constrained('lessons')->cascadeOnDelete();
            $table->unsignedInteger('content_revision');
            $table->string('status', 20)->default('IN_PROGRESS');
            $table->decimal('progress_percent', 5, 2)->default(0);
            $table->jsonb('last_position_json')->nullable();
            $table->timestampTz('started_at')->useCurrent();
            $table->timestampTz('completed_at')->nullable();
            $table->timestampTz('updated_at')->useCurrent();
            $table->primary(['user_id', 'lesson_id']);
        });
        Schema::table('learner_lesson_progress', function (Blueprint $table): void {
            $table->index(['user_id', 'updated_at'], 'idx_lesson_progress_user_updated');
        });
        Schema::create('learner_bookmarks', function (Blueprint $table): void {
            $table->foreignUuid('user_id')->constrained('users')->cascadeOnDelete();
            $table->foreignUuid('lesson_id')->constrained('lessons')->cascadeOnDelete();
            $table->timestampTz('created_at')->useCurrent();
            $table->primary(['user_id', 'lesson_id']);
            $table->index(['user_id', 'created_at'], 'idx_learner_bookmarks_user_created');
        });
        \Illuminate\Support\Facades\DB::statement("ALTER TABLE learner_lesson_progress ADD CONSTRAINT chk_lesson_progress_status CHECK (status IN ('IN_PROGRESS','COMPLETED'))");
        \Illuminate\Support\Facades\DB::statement('ALTER TABLE learner_lesson_progress ADD CONSTRAINT chk_lesson_progress_percent CHECK (progress_percent BETWEEN 0 AND 100)');
    }

    public function down(): void
    {
        Schema::dropIfExists('learner_bookmarks');
        Schema::dropIfExists('learner_lesson_progress');
    }
};
