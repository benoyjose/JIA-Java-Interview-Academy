<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::table('lessons', function (Blueprint $table): void {
            $table->uuid('published_revision_id')->nullable();
            $table->uuid('draft_revision_id')->nullable();
        });
        DB::statement('ALTER TABLE lessons ADD CONSTRAINT fk_lessons_published_revision FOREIGN KEY (published_revision_id) REFERENCES lesson_revisions(id)');
        DB::statement('ALTER TABLE lessons ADD CONSTRAINT fk_lessons_draft_revision FOREIGN KEY (draft_revision_id) REFERENCES lesson_revisions(id)');
        DB::statement(<<<'SQL'
            UPDATE lessons l
            SET published_revision_id = r.id
            FROM lesson_revisions r
            WHERE r.lesson_id = l.id
              AND r.revision_number = l.content_revision
              AND l.status = 'PUBLISHED'
            SQL);
        DB::statement(<<<'SQL'
            ALTER TABLE lessons ADD CONSTRAINT chk_lesson_revision_pointers_distinct
            CHECK (published_revision_id IS NULL OR draft_revision_id IS NULL
                   OR published_revision_id <> draft_revision_id)
            SQL);
        Schema::table('lessons', function (Blueprint $table): void {
            $table->index('published_revision_id', 'idx_lessons_published_revision');
        });
    }

    public function down(): void
    {
        Schema::table('lessons', function (Blueprint $table): void {
            $table->dropIndex('idx_lessons_published_revision');
        });
        DB::statement('ALTER TABLE lessons DROP CONSTRAINT chk_lesson_revision_pointers_distinct');
        DB::statement('ALTER TABLE lessons DROP CONSTRAINT fk_lessons_draft_revision');
        DB::statement('ALTER TABLE lessons DROP CONSTRAINT fk_lessons_published_revision');
        Schema::table('lessons', function (Blueprint $table): void {
            $table->dropColumn(['published_revision_id', 'draft_revision_id']);
        });
    }
};
