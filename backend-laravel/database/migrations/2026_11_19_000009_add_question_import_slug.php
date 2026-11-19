<?php

use Illuminate\\Database\\Migrations\\Migration;
use Illuminate\\Database\\Schema\\Blueprint;
use Illuminate\\Support\\Facades\\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::table('questions', function (Blueprint $table): void {
            $table->string('slug', 120)->nullable();
            $table->unique(['category_id', 'slug'], 'uq_questions_category_slug');
        });
    }

    public function down(): void
    {
        Schema::table('questions', function (Blueprint $table): void {
            $table->dropUnique('uq_questions_category_slug');
            $table->dropColumn('slug');
        });
    }
};
