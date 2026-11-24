# Advanced curriculum coverage register (issues #159 / #191)

This register is a release-control artifact, not a claim that the curriculum is complete or approved. Existing domain lessons are drafts and remain subject to technical review and publication gates in #187.

| Domain | Required coverage | Anchor | Release status |
|---|---|---|---|
| Java language/runtime | versions, collections, generics, streams, concurrency, JVM/GC | advanced-java | draft; depth gate |
| Spring/application engineering | container, Boot, REST, security, transactions, JPA, testing | spring | draft; review |
| Databases/persistence/cache | SQL, indexes, isolation, migrations, pagination, Redis/NoSQL | persistence | draft; review |
| Distributed systems | service boundaries, resilience, idempotency, consistency, sagas | distributed-systems | draft; review |
| Messaging | Kafka, RabbitMQ, ordering, delivery, replay, DLQ | messaging | draft; review |
| System design | NFRs, capacity, HLD/LLD, multi-region, DR, trade-offs | system-design | draft; review |
| Cloud/container/delivery | Docker, Kubernetes, AWS, CI/CD, GitOps, release safety | platform | draft; review |
| Observability/reliability | logs, metrics, tracing, profiling, SLOs, incidents | operations | draft; review |
| Security | OWASP, identity, secrets, privacy, threat modeling | operations | explicit lesson expansion required |
| Engineering leadership | code review, ADRs, delivery, incident leadership, Staff/Principal cases | operations | explicit lesson expansion required |

## Release checks
- Run `node scripts/validate-curriculum-depth.mjs` and `node scripts/validate-curriculum-coverage.mjs`.
- Structural validation does not establish factual correctness, runnable-code evidence, reviewer approval or publication readiness.
- Every released lesson must use stable taxonomy IDs, attribution/provenance, version metadata and revision-bound review.
- #191 owns the reviewed starter pack; this register supports but does not close #159.
