# Issue #159 — Curriculum implementation sequence

This file tracks implementation batches for the advanced curriculum epic. It is an execution plan, not a claim that the lessons listed are already authored or published.

## Existing baseline

- The repository's `docs/JIA_JAVA_CURRICULUM.md` is a broad topic coverage blueprint.
- The existing Java foundations question bank is a reviewed-in-progress draft asset, not a complete deep lesson library.
- Content must follow `docs/CONTENT_MODEL.md`, preserve provenance and remain unpublished until editorial approval.

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

## Per-lesson quality gate

Each lesson must provide: measurable objectives; prerequisites; version scope; concept and internals; executable examples or explicitly marked pseudocode; failure modes; production trade-offs; hands-on exercise and solution/rubric; beginner-to-architect interview questions; concise spoken answer; detailed answer; assessment/revision links; provenance; review state.

## Current first authored lesson

- [x] Draft: Advanced Java — Java Memory Model, Happens-Before and Safe Publication
- [ ] Technical review against JLS and concurrency API contracts
- [ ] Content-model/import integration and validator coverage
- [ ] Editorial approval and publication (separate from merge)

## Completion reporting

Report authored, reviewed, imported and published counts separately. A merged Markdown draft is not a published lesson.