<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('lesson_revisions', function (Blueprint $table): void {
            $table->uuid('id')->primary();
            $table->foreignUuid('lesson_id')->constrained('lessons')->cascadeOnDelete();
            $table->unsignedInteger('revision_number');
            $table->string('title', 200);
            $table->string('summary', 700)->nullable();
            $table->string('difficulty', 20);
            $table->jsonb('content_json')->nullable();
            $table->string('source_type', 30);
            $table->foreignUuid('changed_by')->nullable()->constrained('users');
            $table->string('change_note', 500)->nullable();
            $table->timestampTz('created_at')->useCurrent();
            $table->unique(['lesson_id', 'revision_number'], 'uq_lesson_revision');
        });
        DB::statement("INSERT INTO lesson_revisions(id, lesson_id, revision_number, title, summary, difficulty, content_json, source_type, change_note) SELECT gen_random_uuid(), id, content_revision, title, summary, difficulty, content_json, source_type, 'Baseline revision captured during content versioning rollout' FROM lessons");

        Schema::create('content_review_tasks', function (Blueprint $table): void {
            $table->uuid('id')->primary();
            $table->foreignUuid('lesson_id')->constrained('lessons')->cascadeOnDelete();
            $table->foreignUuid('submitted_by')->constrained('users');
            $table->foreignUuid('assigned_reviewer')->nullable()->constrained('users');
            $table->string('status', 20)->default('PENDING');
            $table->text('review_comment')->nullable();
            $table->timestampTz('submitted_at')->useCurrent();
            $table->timestampTz('reviewed_at')->nullable();
        });
        DB::statement("ALTER TABLE content_review_tasks ADD CONSTRAINT chk_content_review_status CHECK (status IN ('PENDING','APPROVED','CHANGES_REQUESTED','REJECTED'))");
        Schema::table('content_review_tasks', function (Blueprint $table): void {
            $table->index(['status', 'submitted_at'], 'idx_content_review_status_submitted');
        });

        Schema::create('audit_events', function (Blueprint $table): void {
            $table->uuid('id')->primary();
            $table->foreignUuid('actor_user_id')->nullable()->constrained('users');
            $table->foreignUuid('academy_id')->nullable()->constrained('academies');
            $table->string('action', 100);
            $table->string('entity_type', 80);
            $table->uuid('entity_id')->nullable();
            $table->jsonb('metadata_json')->default('{}');
            $table->timestampTz('occurred_at')->useCurrent();
            $table->index(['academy_id', 'occurred_at'], 'idx_audit_events_academy_time');
            $table->index(['actor_user_id', 'occurred_at'], 'idx_audit_events_actor_time');
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('audit_events');
        Schema::dropIfExists('content_review_tasks');
        Schema::dropIfExists('lesson_revisions');
    }
};
