<?php
declare(strict_types=1);
use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;
return new class extends Migration {
 public function up():void {Schema::create('learner_daily_commitments',function(Blueprint $table):void {
  $table->uuid('id')->primary();$table->uuid('user_id')->index();$table->date('local_date');$table->string('timezone',64);
  $table->unsignedSmallInteger('goal_minutes')->default(30);$table->unsignedSmallInteger('completed_minutes')->default(0);
  $table->unsignedSmallInteger('sessions_completed')->default(0);$table->timestamps();
  $table->unique(['user_id','local_date'],'learner_daily_commitment_user_date_unique');
 });}
 public function down():void {Schema::dropIfExists('learner_daily_commitments');}
};
