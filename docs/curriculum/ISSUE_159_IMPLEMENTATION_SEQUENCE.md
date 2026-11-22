# Issue #159 — Curriculum implementation sequence

This is a delivery tracker, not a claim that lessons are published. The repository's curriculum blueprint is broad; this epic authors detailed lesson content.

## Domain delivery order

| Batch | Domain | Coverage |
|---|---|---|
| 1 | Java Memory Model & concurrency | Happens-before, races, volatile, monitors, publication, atomics |
| 2 | Collections, Generics & Streams | Contracts, internals, complexity, erasure, collectors, parallelism |
| 3 | JVM & performance | Class loading, JIT, memory, GC, JFR and production diagnosis |
| 4 | Spring core & Boot | IoC lifecycle, auto-configuration, MVC, transactions, security, readiness |
| 5 | JPA, SQL & data | Persistence context, fetch plans, transactions, indexing, query plans, migrations |
| 6 | Microservices & distributed systems | Resilience, idempotency, consistency, outbox, saga, failure recovery |
| 7 | Kafka & RabbitMQ | Delivery guarantees, partitioning, consumers, retry/DLQ, operations |
| 8 | System design & architecture | Requirements, capacity, trade-offs, HA, multi-region and design cases |
| 9 | Cloud, Kubernetes & delivery | Containers, K8s, AWS, CI/CD, GitOps, release safety |
| 10 | Observability, security & leadership | Telemetry, incidents, threat modelling, architecture, leadership |

## Authored lesson drafts

- [x] Batch 1: [Java Memory Model, Happens-Before and Safe Publication](advanced-java/01-java-memory-model-and-safe-publication.md) — merged PR #160; review/import/publish pending.
- [x] Batch 2: [Collections, Generics and Stream Pipelines](advanced-java/02-collections-generics-and-streams.md) — merged PR #161; review/import/publish pending.
- [x] Batch 3: [JVM Internals, GC and Performance Diagnosis](advanced-java/03-jvm-internals-gc-and-performance.md) — merged PR #162; review/import/publish pending.
- [x] Batch 4: [Spring Container, Boot, Transactions and Security](spring/01-spring-container-boot-transactions-security.md) — authored in current PR; review/import/publish pending.
- [ ] Batch 5: JPA, SQL and data engineering.
- [ ] Batch 6: Microservices and distributed systems.
- [ ] Batch 7: Kafka and RabbitMQ.
- [ ] Batch 8: System design and architecture.
- [ ] Batch 9: Cloud, Kubernetes and delivery.
- [ ] Batch 10: Observability, security and engineering leadership.

## Required lesson quality gate

Each lesson includes measurable outcomes, prerequisites, version scope, internals, examples or clearly labelled pseudocode, failure modes, production trade-offs, exercises/solutions, junior-to-architect interview questions, concise and expanded answers, assessment links, provenance and review state.

## Status accounting

Track authored, technically reviewed, imported, approved and published counts separately. A merged Markdown draft is not a published lesson. Follow `docs/CONTENT_MODEL.md` and retain provenance; publication remains a separate review action.
