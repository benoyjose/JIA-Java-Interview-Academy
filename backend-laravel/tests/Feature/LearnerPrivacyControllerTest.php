<?php

namespace Tests\Feature;

use App\Http\Controllers\Api\V1\LearnerPrivacyController;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Http\Request;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;
use Tests\TestCase;

final class LearnerPrivacyControllerTest extends TestCase
{
    private const USER = '60000000-0000-0000-0000-000000000001';
    private const OTHER = '60000000-0000-0000-0000-000000000002';

    protected function setUp(): void
    {
        parent::setUp();
        foreach (['learner_lesson_progress','learner_bookmarks','quiz_attempt_answers','quiz_attempts',
            'learner_revision_schedules','learner_notification_preferences','learner_notifications','users'] as $table) {
            Schema::dropIfExists($table);
        }
        Schema::create('users', function (Blueprint $t): void {
            $t->uuid('id')->primary(); $t->string('name'); $t->string('email');
            $t->string('status'); $t->string('password_hash')->nullable();
        });
        Schema::create('learner_lesson_progress', function (Blueprint $t): void {
            $t->uuid('user_id'); $t->uuid('lesson_id'); $t->integer('content_revision');
            $t->string('status'); $t->decimal('progress_percent'); $t->json('last_position_json')->nullable();
            $t->timestamp('started_at')->nullable(); $t->timestamp('completed_at')->nullable(); $t->timestamp('updated_at');
        });
        Schema::create('learner_bookmarks', function (Blueprint $t): void {
            $t->uuid('user_id'); $t->uuid('lesson_id'); $t->timestamp('created_at');
        });
        Schema::create('quiz_attempts', function (Blueprint $t): void {
            $t->uuid('id'); $t->uuid('user_id'); $t->uuid('academy_id'); $t->uuid('technology_id')->nullable();
            $t->uuid('category_id')->nullable(); $t->string('difficulty'); $t->string('mode'); $t->string('status');
            $t->integer('question_count'); $t->integer('correct_count')->nullable(); $t->decimal('score_percent')->nullable();
            $t->timestamp('started_at'); $t->timestamp('submitted_at')->nullable(); $t->integer('duration_seconds')->nullable();
        });
        Schema::create('quiz_attempt_answers', function (Blueprint $t): void {
            $t->uuid('attempt_id'); $t->integer('position'); $t->json('response_json')->nullable();
            $t->boolean('is_correct')->nullable(); $t->decimal('marks_awarded')->nullable();
            $t->integer('time_spent_ms')->nullable(); $t->timestamp('answered_at')->nullable();
        });
        Schema::create('learner_revision_schedules', function (Blueprint $t): void {
            $t->uuid('user_id'); $t->uuid('academy_id'); $t->uuid('lesson_id'); $t->integer('stage');
            $t->timestamp('due_at'); $t->timestamp('last_completed_at')->nullable(); $t->timestamp('updated_at');
        });
        Schema::create('learner_notification_preferences', function (Blueprint $t): void {
            $t->uuid('user_id'); $t->uuid('academy_id'); $t->boolean('reminders_enabled'); $t->boolean('push_enabled');
            $t->time('quiet_start')->nullable(); $t->time('quiet_end')->nullable(); $t->string('timezone'); $t->timestamp('updated_at');
        });
        Schema::create('learner_notifications', function (Blueprint $t): void {
            $t->uuid('id'); $t->uuid('user_id'); $t->uuid('academy_id'); $t->string('notification_type');
            $t->string('title'); $t->string('message'); $t->json('payload'); $t->string('status');
            $t->timestamp('scheduled_at'); $t->timestamp('sent_at')->nullable(); $t->timestamp('created_at');
        });
        DB::table('users')->insert([
            ['id'=>self::USER,'name'=>'Learner','email'=>'learner@example.test','status'=>'ACTIVE','password_hash'=>'never-export'],
            ['id'=>self::OTHER,'name'=>'Other','email'=>'other@example.test','status'=>'ACTIVE','password_hash'=>'other-secret'],
        ]);
        DB::table('learner_bookmarks')->insert([
            ['user_id'=>self::USER,'lesson_id'=>'70000000-0000-0000-0000-000000000001','created_at'=>now()],
            ['user_id'=>self::OTHER,'lesson_id'=>'70000000-0000-0000-0000-000000000002','created_at'=>now()],
        ]);
    }

    public function test_export_is_scoped_to_authenticated_user_and_never_returns_password_hash(): void
    {
        $request = Request::create('/api/v1/me/privacy/export');
        $request->attributes->set('jia_user', (object) ['id' => self::USER]);
        $response = (new LearnerPrivacyController())->export($request);
        $payload = $response->getData(true);

        $this->assertSame(1, $payload['formatVersion']);
        $this->assertCount(1, $payload['profile']);
        $this->assertSame('learner@example.test', $payload['profile'][0]['email']);
        $this->assertArrayNotHasKey('password_hash', $payload['profile'][0]);
        $this->assertCount(1, $payload['bookmarks']);
        $this->assertSame(self::USER, $payload['bookmarks'][0]['user_id'] ?? self::USER);
    }

    public function test_export_requires_authenticated_jia_principal(): void
    {
        $request = Request::create('/api/v1/me/privacy/export');
        $this->expectException(\Symfony\Component\HttpKernel\Exception\HttpException::class);
        (new LearnerPrivacyController())->export($request);
    }
}
