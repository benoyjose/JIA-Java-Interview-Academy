# Issue #159 — Curriculum implementation sequence

This file tracks implementation batches for the advanced curriculum epic. It is an execution plan, not a claim that the lessons listed are already authored or published.

## Existing baseline

- `docs/JIA_JAVA_CURRICULUM.md` is the broad coverage blueprint.
- The 100-question Java foundations bank is a question set, not a deep lesson library.
- Content follows `docs/CONTENT_MODEL.md`, preserves provenance and remains unpublished until editorial approval.

## Domain delivery order

| Batch | Domain | Minimum deep coverage |
|---|---|---|
| 1 | Java Memory Model & concurrency | Happens-before, data races, volatile, monitors, safe publication, atomics, executors, virtual threads |
| 2 | Collections, Generics & Streams | Contracts/internals, complexity, type erasure, collectors, parallel-stream hazards |
| 3 | JVM & performance | Class loading, JIT, heap/GC, JFR, profiling and production diagnosis |
| 4 | Spring core & Boot | IoC lifecycle, auto-configuration, MVC, security, configuration and observability |
| 5 | JPA, SQL & data | Persistence context, fetch plans, transactions, indexing, query plans and migration |
| 6 | Microservices & distributed systems | Resilience, idempotency, consistency, outbox, saga, failure recovery |
| 7 | Kafka & RabbitMQ | Delivery guarantees, partitioning, consumer behavior, retry/DLQ and operations |
| 8 | System design & architecture | Requirements, capacity, trade-offs, high availability, multi-region and design case studies |
| 9 | Cloud, Kubernetes & delivery | Container runtime, K8s operations, AWS, CI/CD, GitOps and release safety |
| 10 | Observability, security & leadership | Metrics/logs/traces, incidents, threat modeling, architecture and technical leadership |

## Lesson delivery status

- [x] Batch 1 draft: [Java Memory Model, Happens-Before and Safe Publication](advanced-java/01-java-memory-model-and-safe-publication.md) — merged in PR #160; review/import/publish pending.
- [x] Batch 2 draft: [Collections, Generics and Stream Pipelines](advanced-java/02-collections-generics-and-streams.md) — merged in PR #161; review/import/publish pending.
- [x] Batch 3 draft: [JVM Internals, Garbage Collection and Performance Diagnosis](advanced-java/03-jvm-internals-gc-and-performance.md) — authored in current PR; technical review/import/publish pending.
- [ ] Batch 4: Spring core and Spring Boot.
- [ ] Batch 5: JPA, SQL and data engineering.
- [ ] Batch 6: Microservices and distributed systems.
- [ ] Batch 7: Kafka and RabbitMQ.
- [ ] Batch 8: System design and architecture.
- [ ] Batch 9: Cloud, Kubernetes and delivery.
- [ ] Batch 10: Observability, security and engineering leadership.

## Per-lesson quality gate

Each lesson must provide measurable objectives; prerequisites; version scope; concept and internals; executable examples or explicitly marked pseudocode; failure modes; production trade-offs; hands-on exercise and solution/rubric; beginner-to-architect interview questions; concise spoken answer; detailed answer; assessment/revision links; provenance; review state.

## Completion reporting

Report authored, reviewed, imported and published counts separately. A merged Markdown draft is not a published lesson.
