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
    }

    public function down(): void
    {
        Schema::table('lesson_revisions', function (Blueprint $table): void {
            $table->dropColumn(['source_description', 'attribution']);
        });
    }
};
