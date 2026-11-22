<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::table('academy_apps', function (Blueprint $table): void {
            $table->text('release_notes')->nullable();
            $table->timestampTz('released_at')->nullable();
        });
    }

    public function down(): void
    {
        Schema::table('academy_apps', function (Blueprint $table): void {
            $table->dropColumn(['release_notes', 'released_at']);
        });
    }
};
