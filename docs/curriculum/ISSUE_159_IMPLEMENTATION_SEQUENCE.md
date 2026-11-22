# Issue #159 — Curriculum implementation sequence

Track authored, technically reviewed, imported, approved and published states separately. The broad topic blueprint is not itself completion.

## Domain sequence

1. Java language, memory model and concurrency
2. Collections, generics and streams
3. JVM internals and performance
4. Spring Framework and Boot
5. JPA, Hibernate, SQL and data engineering
6. Microservices and distributed systems
7. Kafka and RabbitMQ
8. System design and architecture
9. Cloud, Kubernetes and delivery
10. Observability, security and engineering leadership

## Deep lesson drafts

- [x] Java Memory Model and safe publication — PR #160 merged; review/import/publish pending.
- [x] Collections, generics and stream pipelines — PR #161 merged; review/import/publish pending.
- [x] JVM internals, GC and performance diagnosis — PR #162 merged; review/import/publish pending.
- [x] Spring container, Boot, transactions and security — PR #163 merged; review/import/publish pending.
- [x] [JPA, Hibernate, SQL and data engineering](persistence/01-jpa-hibernate-sql-and-data-engineering.md) — PR #164 merged; review/import/publish pending.
- [x] [Microservices, resilience, consistency and sagas](distributed-systems/01-microservices-resilience-consistency-and-sagas.md) — authored in current PR; review/import/publish pending.
- [ ] Kafka and RabbitMQ.
- [ ] System design and architecture.
- [ ] Cloud, Kubernetes and delivery.
- [ ] Observability, security and leadership.

## Lesson quality gate

Every lesson needs outcomes, prerequisites, version scope, deep concepts/internals, validated code/SQL or labelled pseudocode, edge cases, production failure modes, performance/security/operational trade-offs, exercises with solutions/rubrics, junior-to-architect interview questions, concise and detailed answers, revision links, source provenance and editorial state.

## Content integrity and release

Follow `docs/CONTENT_MODEL.md` and the versioned content pipeline. Validate IDs/slugs, required fields, references, quiz integrity, version labels and offline compatibility. Merged Markdown is draft content—not imported or published learner content.
