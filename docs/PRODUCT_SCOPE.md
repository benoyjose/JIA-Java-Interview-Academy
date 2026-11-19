# JIA Product Scope

JIA is the first academy on a shared platform designed to support future PIA (Python Interview Academy) and GIA (Go Interview Academy) products. Shared platform services must be reusable; curricula and lesson content remain academy-specific. See [Multi-Academy Platform Strategy](MULTI_ACADEMY_PLATFORM.md).

## 1. Goal

Build a practical technical learning and interview-preparation product for Java/backend engineers. The experience must help a learner understand a topic, implement it, answer interview questions verbally, and identify weak areas through assessment.

## 2. Primary user experience

### Learner app

- Home dashboard: continue learning, daily goal, streak, progress and recommendations.
- Technology catalogue with tracks, modules and lessons.
- Lesson reader with code samples, diagrams, common mistakes and interview questions.
- Quick revision: flashcards, bookmarks and concise answer cards.
- Quiz and mock interview modes.
- Search across lessons, questions and code.
- Progress dashboard with topic-level strengths and gaps.
- Offline access with background content synchronization.
- Settings for theme, reminders, download and sync preferences.

### Admin portal

- Role-based access for administrators, editors and reviewers.
- Manage technologies, categories, modules and lessons.
- Rich lesson editor with code blocks and media.
- Question bank and quiz builder.
- Preview as learner before publishing.
- Draft, review, publish, unpublish and rollback workflow.
- Content version history and audit trail.
- Bulk import/export for structured content.
- App release management for APK metadata and download artifacts.
- Basic learner, usage and assessment analytics.

## 3. Standard lesson format

Every lesson should support:

1. Short definition and learning objectives.
2. Core concept and internal working.
3. Practical implementation with compilable code where applicable.
4. Real-world use cases and engineering trade-offs.
5. Common mistakes and troubleshooting.
6. Interview questions with concise, speakable answers.
7. Expandable detailed explanations and follow-up questions.
8. Topic quiz and links to prerequisite/next lessons.

The concise answer is the default presentation. Detailed explanation and code are expandable so learners can respond quickly during live interviews.

## 4. Assessment

Question types:
- Single-choice and multiple-select.
- True/false.
- Predict program output.
- Find and fix a bug.
- Select or write an implementation.
- SQL query and optimization.
- Architecture and production incident scenarios.

Assessment modes:
- Quick practice (5 questions).
- Topic assessment (configurable question count).
- Timed mock interview.
- Weak-area practice.
- Mixed daily challenge.
- Retry incorrect questions.

Store attempt history, selected answers, correctness, time spent, score and topic-level outcomes. Explanations should be available after submission or according to quiz configuration.

## 5. Initial learning tracks

The detailed coverage map is maintained in [JIA Java Curriculum](JIA_JAVA_CURRICULUM.md). It expands these tracks into language foundations, modern Java, collections, JVM, concurrency, I/O, patterns, build tooling, testing, Spring, persistence, databases, distributed systems, messaging, caching, security, performance, cloud, architecture, leadership and coding interviews.

### Initial learning tracks

1. Core Java — language fundamentals, OOP, collections, generics, exceptions, streams, lambdas, JVM and garbage collection.
2. Advanced Java — concurrency, executors, CompletableFuture, virtual threads, JDBC and performance.
3. Spring Framework — IoC, dependency injection, beans, AOP, MVC and transactions.
4. Spring Boot — auto-configuration, REST APIs, validation, security, Actuator and testing.
5. Hibernate/JPA — entity mapping, relationships, fetching, N+1, caching, pagination and transactions.
6. Microservices — service boundaries, gateway, discovery, resilience, Saga, CQRS and distributed transactions.
7. Messaging — Kafka and RabbitMQ, delivery semantics, ordering, retries, dead-letter queues and consumer scaling.
8. Databases — SQL, indexing, query plans, Oracle, PostgreSQL, MySQL, Redis and MongoDB.
9. System Design — HLD, LLD, SOLID, design patterns, scalability, availability and consistency.
10. DevOps — Git, Docker, Kubernetes, CI/CD, Jenkins and deployment strategies.
11. Cloud and Observability — AWS, metrics, logs, tracing, Grafana and Splunk.
12. Engineering Excellence — testing, security, performance, debugging and production readiness.

Java version comparisons must clearly distinguish Java 8, 11, 17 and 21+ behavior.

## 6. Content governance

Each content item must record its provenance:
- `USER_ORIGINAL`: user-provided material; highest priority.
- `ADMIN_AUTHORED`: content authored in the JIA admin portal.
- `EXTERNAL_REFERENCE`: external material, with attribution and review.

Rules:
- Preserve original wording, examples and organization when importing user material.
- Do not silently replace or rewrite user-owned content.
- Add missing context as a clearly separated supplement.
- Flag incorrect or unsafe code for review; do not silently overwrite it.
- Track revisions and allow rollback.
- Detect likely duplicates, but require review before merging.
- Do not publish unreviewed AI-generated or external content.

## 7. Non-functional requirements

- Offline-first lesson reading and quiz capability for downloaded content.
- Secure authentication and role-based authorization.
- Versioned content manifest and incremental synchronization.
- Content release independent of APK release.
- Audit trail for admin mutations and publishing.
- Accessible, responsive UI and readable code presentation.
- Automated tests for critical business rules and API contracts.

## 8. Delivery sequence

1. Content schema, taxonomy and seed examples.
2. Backend foundation: authentication, catalogue, lessons and content manifest.
3. Admin portal: content editing, review and publishing.
4. Android foundation: sign-in, catalogue, lesson reader and offline cache.
5. Quiz, progress, search and mock interview capabilities.
6. APK release management, observability, security hardening and deployment.
