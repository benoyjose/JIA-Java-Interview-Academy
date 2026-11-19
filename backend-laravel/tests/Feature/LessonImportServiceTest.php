<?php

namespace Tests\Feature;

use App\Services\LessonImportService;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;
use Symfony\Component\HttpKernel\Exception\ConflictHttpException;
use Tests\TestCase;

final class LessonImportServiceTest extends TestCase
{
    protected function setUp(): void
    {
        parent::setUp();
        foreach (['audit_events', 'lesson_revisions', 'lessons', 'categories', 'technologies'] as $table) {
            Schema::dropIfExists($table);
        }
        Schema::create('technologies', function (Blueprint $table): void {
            $table->uuid('id')->primary();
            $table->uuid('academy_id');
        });
        Schema::create('categories', function (Blueprint $table): void {
            $table->uuid('id')->primary();
            $table->string('slug', 100)->unique();
            $table->uuid('technology_id');
        });
        Schema::create('lessons', function (Blueprint $table): void {
            $table->uuid('id')->primary();
            $table->uuid('category_id');
            $table->string('slug', 120)->unique();
            $table->string('title', 200);
            $table->string('summary', 700)->nullable();
            $table->string('difficulty', 20);
            $table->jsonb('content_json')->nullable();
            $table->unsignedInteger('content_revision');
            $table->string('source_type', 30);
            $table->string('status', 20);
            $table->uuid('published_revision_id')->nullable();
            $table->uuid('draft_revision_id')->nullable();
            $table->timestampsTz();
        });
        Schema::create('lesson_revisions', function (Blueprint $table): void {
            $table->uuid('id')->primary();
            $table->uuid('lesson_id');
            $table->unsignedInteger('revision_number');
            $table->string('title', 200);
            $table->string('summary', 700)->nullable();
            $table->string('difficulty', 20);
            $table->jsonb('content_json')->nullable();
            $table->string('source_type', 30);
            $table->string('source_description', 500)->nullable();
            $table->string('attribution', 1000)->nullable();
            $table->uuid('changed_by')->nullable();
            $table->string('change_note', 500)->nullable();
            $table->timestampTz('created_at');
        });
        Schema::create('audit_events', function (Blueprint $table): void {
            $table->uuid('id')->primary();
            $table->uuid('actor_user_id')->nullable();
            $table->uuid('academy_id')->nullable();
            $table->string('action', 100);
            $table->string('entity_type', 80);
            $table->uuid('entity_id')->nullable();
            $table->jsonb('metadata_json');
            $table->timestampTz('occurred_at');
        });
        DB::table('technologies')->insert(['id' => '10000000-0000-0000-0000-000000000001', 'academy_id' => '50000000-0000-0000-0000-000000000001']);
        DB::table('categories')->insert(['id' => '20000000-0000-0000-0000-000000000001', 'slug' => 'java-language-fundamentals', 'technology_id' => '10000000-0000-0000-0000-000000000001']);
    }

    public function test_imports_draft_and_persists_provenance_without_publishing(): void
    {
        $slug = 'laravel-import-'.uniqid();
        $result = (new LessonImportService())->import($this->batch($slug), (object) ['id' => '60000000-0000-0000-0000-000000000001']);

        $this->assertSame(1, $result['importedCount']);
        $this->assertSame('DRAFT', $result['lessons'][0]['status']);
        $this->assertDatabaseHas('lessons', ['slug' => $slug, 'status' => 'DRAFT', 'published_revision_id' => null]);
        $this->assertDatabaseHas('lesson_revisions', ['source_description' => 'Oracle Java documentation', 'attribution' => 'Oracle']);
        $this->assertDatabaseHas('audit_events', ['action' => 'LESSON_DRAFT_IMPORTED']);
    }

    public function test_existing_slug_conflict_does_not_create_partial_batch(): void
    {
        $slug = 'laravel-conflict-'.uniqid();
        (new LessonImportService())->import($this->batch($slug), (object) ['id' => '60000000-0000-0000-0000-000000000001']);
        $this->expectException(ConflictHttpException::class);
        (new LessonImportService())->import($this->batch($slug), (object) ['id' => '60000000-0000-0000-0000-000000000001']);
    }

    private function batch(string $slug): array
    {
        return [
            'formatVersion' => 1,
            'lessons' => [[
                'categorySlug' => 'java-language-fundamentals',
                'slug' => $slug,
                'title' => 'Imported lesson',
                'summary' => 'Imported by integration test',
                'difficulty' => 'BEGINNER',
                'provenance' => [
                    'sourceType' => 'EXTERNAL_REFERENCE',
                    'sourceDescription' => 'Oracle Java documentation',
                    'attribution' => 'Oracle',
                ],
                'content' => [
                    'schemaVersion' => 1,
                    'blocks' => [['type' => 'markdown', 'key' => 'overview', 'body' => 'A lesson body']],
                ],
                'changeNote' => 'Integration import',
            ]],
        ];
    }
}
