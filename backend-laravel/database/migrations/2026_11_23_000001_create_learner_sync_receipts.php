<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('learner_sync_receipts', function (Blueprint $table): void {
            $table->foreignUuid('user_id')->constrained('users')->cascadeOnDelete();
            $table->uuid('event_id');
            $table->char('event_hash', 64);
            $table->timestampTz('created_at')->useCurrent();
            $table->primary(['user_id', 'event_id']);
            $table->index(['user_id', 'created_at'], 'idx_sync_receipts_user_created');
        });
    }

    public function down(): void
    {
        Schema::dropIfExists('learner_sync_receipts');
    }
};
