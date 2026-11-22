# Issue #159 — Curriculum implementation sequence

This tracker distinguishes authored drafts from technically reviewed, imported and published lessons. The broad curriculum blueprint is not itself completion.

## Domain delivery order

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
- [x] [JPA, Hibernate, SQL and data engineering](persistence/01-jpa-hibernate-sql-and-data-engineering.md) — authored in current PR; review/import/publish pending.
- [ ] Microservices and distributed systems.
- [ ] Kafka and RabbitMQ.
- [ ] System design and architecture.
- [ ] Cloud, Kubernetes and delivery.
- [ ] Observability, security and leadership.

## Per-lesson quality gate

Each lesson must have outcomes, prerequisites, version scope, conceptual depth and internals, validated code/SQL or clearly labelled pseudocode, edge cases, production failure modes, performance/security/operational trade-offs, exercises with solutions/rubrics, junior-to-architect interview questions, concise spoken and detailed answers, revision/assessment links, source provenance and review state.

## Content integrity and publication

Follow `docs/CONTENT_MODEL.md` and the versioned content pipeline. Validate stable IDs/slugs, required fields, references, quiz answer integrity, version labels and offline compatibility. Track authored, technically reviewed, imported, approved and published counts independently. A merged Markdown draft is not a published lesson.
