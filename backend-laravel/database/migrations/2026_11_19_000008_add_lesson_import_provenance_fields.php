<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::table('lesson_revisions', function (Blueprint $table): void {
            $table->string('source_description', 500)->nullable();
            $table->string('attribution', 1000)->nullable();
        });
        Schema::table('content_review_tasks', function (Blueprint $table): void {
            $table->uuid('revision_id')->nullable();
            $table->uuid('reviewed_by')->nullable();
        });
        Schema::table('content_review_tasks', function (Blueprint $table): void {
            $table->foreign('revision_id')->references('id')->on('lesson_revisions');
            $table->foreign('reviewed_by')->references('id')->on('users');
        });
    }

    public function down(): void
    {
        Schema::table('content_review_tasks', function (Blueprint $table): void {
            $table->dropForeign(['revision_id']);
            $table->dropForeign(['reviewed_by']);
            $table->dropColumn(['revision_id', 'reviewed_by']);
        });
        Schema::table('lesson_revisions', function (Blueprint $table): void {
            $table->dropColumn(['source_description', 'attribution']);
        });
    }
};
