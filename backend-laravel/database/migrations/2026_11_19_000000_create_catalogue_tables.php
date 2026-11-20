<?php

use Illuminate\\Database\\Migrations\\Migration;
use Illuminate\\Database\\Schema\\Blueprint;
use Illuminate\\Support\\Facades\\DB;
use Illuminate\\Support\\Facades\\Schema;

return new class extends Migration
{
    public function up(): void
    {
        Schema::create('technologies', function (Blueprint $table): void {
            $table->uuid('id')->primary();
            $table->string('slug', 80)->unique();
            $table->string('name', 140);
            $table->string('description', 500)->nullable();
            $table->integer('sort_order')->default(0);
            $table->string('status', 20);
            $table->timestampsTz();
        });
        Schema::create('categories', function (Blueprint $table): void {
            $table->uuid('id')->primary();
            $table->foreignUuid('technology_id')->constrained('technologies');
            $table->foreignUuid('parent_id')->nullable()->constrained('categories');
            $table->string('slug', 100)->unique();
            $table->string('name', 160);
            $table->integer('sort_order')->default(0);
            $table->string('status', 20);
            $table->timestampsTz();
            $table->index(['technology_id', 'status', 'sort_order'], 'idx_categories_technology_status_order');
        });
        Schema::create('lessons', function (Blueprint $table): void {
            $table->uuid('id')->primary();
            $table->foreignUuid('category_id')->constrained('categories');
            $table->string('slug', 120)->unique();
            $table->string('title', 200);
            $table->string('summary', 700)->nullable();
            $table->string('difficulty', 20);
            $table->jsonb('content_json')->nullable();
            $table->unsignedInteger('content_revision')->default(1);
            $table->string('source_type', 30);
            $table->string('status', 20);
            $table->timestampsTz();
            $table->index(['category_id', 'status'], 'idx_lessons_category_status');
        });

        DB::table('technologies')->insert([
            ['id'=>'10000000-0000-0000-0000-000000000001','slug'=>'core-java','name'=>'Core Java','description'=>'Java language fundamentals and core platform concepts.','sort_order'=>1,'status'=>'PUBLISHED'],
            ['id'=>'10000000-0000-0000-0000-000000000002','slug'=>'advanced-java','name'=>'Advanced Java','description'=>'Concurrency, JVM internals and advanced language capabilities.','sort_order'=>2,'status'=>'PUBLISHED'],
            ['id'=>'10000000-0000-0000-0000-000000000003','slug'=>'spring-framework','name'=>'Spring Framework','description'=>'Dependency injection, application context, AOP and MVC.','sort_order'=>3,'status'=>'PUBLISHED'],
            ['id'=>'10000000-0000-0000-0000-000000000004','slug'=>'spring-boot','name'=>'Spring Boot','description'=>'Production-ready Spring applications and services.','sort_order'=>4,'status'=>'PUBLISHED'],
            ['id'=>'10000000-0000-0000-0000-000000000005','slug'=>'hibernate-jpa','name'=>'Hibernate and JPA','description'=>'Persistence, mapping, fetching and transaction behavior.','sort_order'=>5,'status'=>'PUBLISHED'],
            ['id'=>'10000000-0000-0000-0000-000000000006','slug'=>'microservices','name'=>'Microservices','description'=>'Distributed service architecture and operational patterns.','sort_order'=>6,'status'=>'PUBLISHED'],
            ['id'=>'10000000-0000-0000-0000-000000000007','slug'=>'messaging','name'=>'Messaging: Kafka and RabbitMQ','description'=>'Asynchronous messaging, delivery semantics and scaling.','sort_order'=>7,'status'=>'PUBLISHED'],
            ['id'=>'10000000-0000-0000-0000-000000000008','slug'=>'databases','name'=>'Databases','description'=>'SQL, data modeling, indexing and NoSQL systems.','sort_order'=>8,'status'=>'PUBLISHED'],
            ['id'=>'10000000-0000-0000-0000-000000000009','slug'=>'system-design','name'=>'System Design','description'=>'High-level design, low-level design and distributed systems.','sort_order'=>9,'status'=>'PUBLISHED'],
            ['id'=>'10000000-0000-0000-0000-000000000010','slug'=>'devops','name'=>'DevOps and CI/CD','description'=>'Build, delivery, containers and orchestration.','sort_order'=>10,'status'=>'PUBLISHED'],
            ['id'=>'10000000-0000-0000-0000-000000000011','slug'=>'cloud-observability','name'=>'Cloud and Observability','description'=>'AWS, metrics, logs, tracing and monitoring.','sort_order'=>11,'status'=>'PUBLISHED'],
            ['id'=>'10000000-0000-0000-0000-000000000012','slug'=>'engineering-excellence','name'=>'Engineering Excellence','description'=>'Testing, security, performance and troubleshooting.','sort_order'=>12,'status'=>'PUBLISHED'],
        ]);
        DB::table('categories')->insert([
            ['id'=>'20000000-0000-0000-0000-000000000001','technology_id'=>'10000000-0000-0000-0000-000000000001','slug'=>'java-language-fundamentals','name'=>'Language Fundamentals','sort_order'=>1,'status'=>'PUBLISHED'],
            ['id'=>'20000000-0000-0000-0000-000000000002','technology_id'=>'10000000-0000-0000-0000-000000000001','slug'=>'java-collections','name'=>'Collections Framework','sort_order'=>2,'status'=>'PUBLISHED'],
            ['id'=>'20000000-0000-0000-0000-000000000003','technology_id'=>'10000000-0000-0000-0000-000000000001','slug'=>'java-streams-lambdas','name'=>'Streams and Lambdas','sort_order'=>3,'status'=>'PUBLISHED'],
            ['id'=>'20000000-0000-0000-0000-000000000004','technology_id'=>'10000000-0000-0000-0000-000000000004','slug'=>'spring-boot-fundamentals','name'=>'Spring Boot Fundamentals','sort_order'=>1,'status'=>'PUBLISHED'],
            ['id'=>'20000000-0000-0000-0000-000000000005','technology_id'=>'10000000-0000-0000-0000-000000000006','slug'=>'microservices-fundamentals','name'=>'Microservices Fundamentals','sort_order'=>1,'status'=>'PUBLISHED'],
        ]);
    }

    public function down(): void
    {
        Schema::dropIfExists('lessons');
        Schema::dropIfExists('categories');
        Schema::dropIfExists('technologies');
    }
};
