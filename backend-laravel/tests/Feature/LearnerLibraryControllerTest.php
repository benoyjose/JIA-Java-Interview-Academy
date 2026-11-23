<?php

namespace Tests\Feature;

use App\Http\Controllers\Api\V1\LearnerLibraryController;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;
use Tests\TestCase;

final class LearnerLibraryControllerTest extends TestCase
{
    protected function setUp(): void
    {
        parent::setUp();
        foreach (['learner_bookmarks', 'learner_lesson_progress', 'lesson_revisions', 'lessons', 'categories', 'users'] as $table) {
            Schema::dropIfExists($table);
        }
        Schema::create('users', function (Blueprint $table): void {
            $table->uuid('id')->primary();
        });
        Schema::create('categories', function (Blueprint $table): void {
            $table->uuid('id')->primary();
        });
        Schema::create('lessons', function (Blueprint $table): void {
            $table->uuid('id')->primary();
            $table->uuid('category_id');
            $table->string('slug', 120)->unique();
            $table->string('title', 200);
            $table->string('status', 20);
            $table->uuid('published_revision_id')->nullable();
        });
        Schema::create('lesson_revisions', function (Blueprint $table): void {
            $table->uuid('id')->primary();
            $table->uuid('lesson_id');
            $table->unsignedInteger('revision_number');
        });
        Schema::create('learner_lesson_progress', function (Blueprint $table): void {
            $table->uuid('user_id');
            $table->uuid('lesson_id');
            $table->unsignedInteger('content_revision');
            $table->string('status', 20);
            $table->decimal('progress_percent', 5, 2);
            $table->jsonb('last_position_json')->nullable();
            $table->timestampTz('started_at')->useCurrent();
            $table->timestampTz('completed_at')->nullable();
            $table->timestampTz('updated_at')->useCurrent();
            $table->primary(['user_id', 'lesson_id']);
        });
        Schema::create('learner_bookmarks', function (Blueprint $table): void {
            $table->uuid('user_id');
            $table->uuid('lesson_id');
            $table->timestampTz('created_at')->useCurrent();
            $table->primary(['user_id', 'lesson_id']);
        });
        DB::table('users')->insert(['id' => '60000000-0000-0000-0000-000000000001']);
        DB::table('categories')->insert(['id' => '20000000-0000-0000-0000-000000000001']);
        DB::table('lessons')->insert([
            'id' => '73000000-0000-0000-0000-000000000001',
            'category_id' => '20000000-0000-0000-0000-000000000001',
            'slug' => 'laravel-progress-test',
            'title' => 'Progress test',
            'status' => 'PUBLISHED',
            'published_revision_id' => '74000000-0000-0000-0000-000000000001',
        ]);
        DB::table('lesson_revisions')->insert([
            'id' => '74000000-0000-0000-0000-000000000001',
            'lesson_id' => '73000000-0000-0000-0000-000000000001',
            'revision_number' => 3,
        ]);
    }

    public function test_progress_upsert_is_monotonic_and_bookmarks_are_idempotent(): void
    {
        $user = (object) ['id' => '60000000-0000-0000-0000-000000000001'];
        $controller = new LearnerLibraryController();
        $request = Request::create('/api/v1/me/lesson-progress/laravel-progress-test', 'PUT', [
            'contentRevision' => 1,
            'progressPercent' => 100,
            'lastPosition' => ['blockId' => 'final'],
        ]);
        $request->attributes->set('jia_user', $user);
        $controller->updateProgress($request, 'laravel-progress-test');

        $request = Request::create('/api/v1/me/lesson-progress/laravel-progress-test', 'PUT', [
            'contentRevision' => 1,
            'progressPercent' => 10,
        ]);
        $request->attributes->set('jia_user', $user);
        $controller->updateProgress($request, 'laravel-progress-test');

        $this->assertDatabaseHas('learner_lesson_progress', [
            'user_id' => $user->id,
            'lesson_id' => '73000000-0000-0000-0000-000000000001',
            'status' => 'COMPLETED',
            'progress_percent' => 100,
        ]);

        $bookmarkRequest = Request::create('/api/v1/me/bookmarks/laravel-progress-test', 'PUT');
        $bookmarkRequest->attributes->set('jia_user', $user);
        $controller->bookmark($bookmarkRequest, 'laravel-progress-test');
        $controller->bookmark($bookmarkRequest, 'laravel-progress-test');
        $this->assertDatabaseCount('learner_bookmarks', 1);

        $controller->removeBookmark($bookmarkRequest, 'laravel-progress-test');
        $this->assertDatabaseCount('learner_bookmarks', 0);
    }
    public function test_stale_progress_cannot_regress_revision_or_reading_position(): void
    {
        $user = (object) ['id' => '60000000-0000-0000-0000-000000000001'];
        $controller = new LearnerLibraryController();
        $first = Request::create('/api/v1/me/lesson-progress/laravel-progress-test', 'PUT', [
            'contentRevision' => 3, 'progressPercent' => 65,
            'lastPosition' => ['blockId' => 'newer'],
        ]);
        $first->attributes->set('jia_user', $user);
        $controller->updateProgress($first, 'laravel-progress-test');

        $stale = Request::create('/api/v1/me/lesson-progress/laravel-progress-test', 'PUT', [
            'contentRevision' => 1, 'progressPercent' => 20,
            'lastPosition' => ['blockId' => 'stale'],
        ]);
        $stale->attributes->set('jia_user', $user);
        $controller->updateProgress($stale, 'laravel-progress-test');

        $row = DB::table('learner_lesson_progress')->where('user_id', $user->id)->first();
        $this->assertSame(3, (int) $row->content_revision);
        $this->assertSame(65.0, (float) $row->progress_percent);
        $this->assertSame('newer', json_decode($row->last_position_json, true)['blockId']);
    }

}
