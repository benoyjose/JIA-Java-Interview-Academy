<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Schema;

return new class extends Migration {
    public function up(): void {
        Schema::create('learner_review_schedule', function (Blueprint $table): void {
            $table->foreignUuid('user_id')->constrained('users')->cascadeOnDelete();
            $table->foreignUuid('lesson_id')->constrained('lessons')->cascadeOnDelete();
            $table->unsignedSmallInteger('repetitions')->default(0);
            $table->decimal('ease_factor', 4, 3)->default(2.500);
            $table->unsignedSmallInteger('interval_days')->default(0);
            $table->unsignedSmallInteger('last_grade')->nullable();
            $table->unsignedSmallInteger('model_version')->default(1);
            $table->timestampTz('last_reviewed_at')->nullable();
            $table->timestampTz('due_at')->index();
            $table->timestampsTz();
            $table->primary(['user_id', 'lesson_id']);
            $table->index(['user_id', 'due_at'], 'idx_review_due_user');
        });
        DB::statement('ALTER TABLE learner_review_schedule ADD CONSTRAINT chk_review_grade CHECK (last_grade IS NULL OR last_grade BETWEEN 0 AND 5)');
        DB::statement('ALTER TABLE learner_review_schedule ADD CONSTRAINT chk_review_ease CHECK (ease_factor BETWEEN 1.300 AND 3.000)');
    }
    public function down(): void { Schema::dropIfExists('learner_review_schedule'); }
};
