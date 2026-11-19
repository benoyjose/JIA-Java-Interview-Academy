# JIA — Java Interview Academy: Curriculum Blueprint

Status: coverage architecture only; not published lesson content.

JIA must serve junior, mid-level, senior, lead, staff/principal and architect interviews. Every topic is version-tagged and linked to prerequisites, skills, roles, lessons and assessments. Each lesson requires a concise speakable answer, detailed explanation, tested example, real-world trade-offs, failure modes and follow-up questions.

## Learning paths
1. Java Foundations
2. Java Developer
3. Advanced Java and JVM
4. Backend Java Engineer
5. Senior/Lead Java Engineer
6. Staff/Principal Engineer and Java Architect
7. Interview Practice and Mock Interviews

## Full Java domain coverage

### A. Language foundations and object model
- JDK/JRE/JVM, compilation, bytecode, javac, launcher, classpath and module path.
- Primitive/reference types, wrappers, literals, conversions, promotion, overflow and initialization.
- Variables, scope, operators, control flow, loops, arrays, varargs, methods and recursion.
- Pass-by-value, overload resolution, constructors, initialization order, this and super.
- Classes, objects, encapsulation, inheritance, composition, polymorphism, abstraction and interfaces.
- Access modifiers, packages, nested/inner/local/anonymous classes.
- Object identity, equals/hashCode contract, toString and cloning caveats.
- Strings, immutability, pool, StringBuilder/StringBuffer, Unicode and text blocks.
- Enums, records, sealed classes, pattern matching and modern switch.
- Generics, bounds, wildcards, PECS, type erasure, bridge methods, raw types and heap pollution.
- Exceptions, checked/unchecked, try-with-resources, suppressed exceptions and custom hierarchies.
- Annotations, meta-annotations, retention, targets, processors and reflection.
- Java modules (JPMS), services, encapsulation and migration.
- Core APIs: Optional, Objects, BigDecimal, UUID, Locale, Math and date/time API.
- Instant, LocalDate, LocalDateTime, ZonedDateTime, Duration, Period, ZoneId, DST and formatting.

### B. Modern Java and functional programming
- Lambdas, functional interfaces, method references, effectively-final capture.
- Stream API: lazy evaluation, intermediate/terminal operations, collectors, reduce, grouping, partitioning, primitive streams and parallel-stream caveats.
- Optional, functional composition, predicates, functions, consumers, suppliers and Bi-functions.
- Default/static interface methods, var inference, records, sealed types and pattern matching.
- Java platform evolution across LTS releases; compatibility, deprecation, preview and incubator distinction.
- Virtual threads, structured concurrency and scoped values, with explicit version/stability labels.
- Foreign Function & Memory API and other advanced platform capabilities.

### C. Collections and data structures
- Iterable, Collection, List, Set, Queue, Deque and Map contracts.
- ArrayList, LinkedList, Vector, Stack, HashMap, LinkedHashMap, TreeMap, ConcurrentHashMap.
- Hashing, buckets, collisions, tree bins, resizing, load factor and capacity.
- HashSet, LinkedHashSet, TreeSet, EnumSet, PriorityQueue and ArrayDeque.
- WeakHashMap, IdentityHashMap, EnumMap, blocking and concurrent collections.
- Iterators, fail-fast vs weakly consistent behavior, ConcurrentModificationException.
- Comparable, Comparator, sorting stability, comparator contract.
- Immutable/unmodifiable collections, defensive copies and copy-on-write.
- Complexity, memory and data-structure selection by workload.

### D. JVM, runtime and memory
- JVM architecture, bytecode, interpreter, JIT, tiered compilation and deoptimization.
- Class loading lifecycle, parent delegation, custom classloaders, linkage and initialization.
- Runtime data areas: heap, Java stacks, PC registers, native stacks, metaspace and code cache.
- Object layout, headers, alignment, compressed references, escape analysis and allocation caveats.
- Garbage collection fundamentals, reachability, generations/regions and reference types.
- G1, ZGC, Shenandoah and collector choice by JDK version, latency and throughput.
- Stop-the-world pauses, safepoints, allocation pressure, GC logs and tuning.
- Memory leaks, classloader leaks, OutOfMemoryError variants and native memory.
- Java Memory Model, happens-before, visibility, ordering, atomicity and safe publication.
- jcmd, jstack, jmap, JFR, Mission Control, heap dumps, Native Memory Tracking and JVM flags.
- Container-aware JVM sizing and production diagnostics.

### E. Concurrency and parallelism
- Process/thread, lifecycle, platform threads and virtual threads.
- Runnable, Callable, Future, CompletableFuture and async composition.
- synchronized, monitors, volatile, happens-before and double-checked locking.
- Atomic variables, CAS, VarHandle, locks, ReentrantLock, ReadWriteLock, StampedLock and Condition.
- ExecutorService, pools, work queues, rejection policies and lifecycle.
- ForkJoinPool, work stealing, parallel streams and executor selection.
- ConcurrentHashMap, BlockingQueue, concurrent iteration.
- CountDownLatch, CyclicBarrier, Semaphore, Phaser and Exchanger.
- Deadlock, livelock, starvation, race conditions, false sharing and thread safety.
- Immutability, confinement, safe publication and concurrent design patterns.
- CompletableFuture exceptions, timeout, cancellation and composition.
- Virtual-thread pinning, resource limits, ThreadLocal caveats and appropriate use cases.
- Structured concurrency/cancellation with version-specific qualification.
- Throughput, latency, queueing, backpressure and bounded concurrency.

### F. I/O, networking and serialization
- java.io streams, readers/writers, buffering and resource lifecycle.
- NIO channels, buffers, selectors, paths, files and watch services.
- TCP/UDP, sockets, HTTP, TLS, DNS and connection lifecycle.
- Serialization risks, Serializable, serialVersionUID and safer formats.
- JSON/Jackson, XML, CSV, Protocol Buffers, schema evolution and compatibility.
- Compression, checksums, encodings, streaming large files and backpressure.

### G. Design principles, patterns and code quality
- SOLID, DRY, KISS, YAGNI, cohesion, coupling and separation of concerns.
- Composition over inheritance, dependency inversion and encapsulation.
- GoF patterns: Factory, Abstract Factory, Builder, Singleton, Prototype, Adapter, Decorator, Facade, Proxy, Composite, Bridge, Flyweight, Strategy, Observer, Template Method, Command, State, Chain of Responsibility, Iterator and Mediator.
- Dependency injection, inversion of control and service boundaries.
- DDD: entities, value objects, aggregates, repositories, domain services and bounded contexts.
- Hexagonal/ports-and-adapters, clean, layered and modular-monolith architecture.
- Refactoring, code smells, API design, backward compatibility and maintainability.

### H. Build, dependency and developer tooling
- Maven lifecycle, POM, scopes, BOM, plugins, profiles and multi-module builds.
- Gradle tasks, configurations, plugins, version catalogs, toolchains and multi-project builds.
- Dependency resolution, conflicts, locking, semantic versioning and reproducible builds.
- SBOM, vulnerability scanning and software supply-chain security.
- Git workflows, branching, code review, merge strategies and release tagging.
- IDE debugging, remote debugging, profiling and static analysis.
- Checkstyle/Spotless, PMD, SpotBugs and Error Prone.

### I. Testing and quality engineering
- Unit, component, integration, contract, end-to-end, smoke and regression tests.
- JUnit 5, assertions, lifecycle, parameterized tests, extensions and suites.
- Mockito, stubbing, verification, spies and test-double trade-offs.
- Spring test slices, MockMvc, WebTestClient and Testcontainers.
- REST tests, consumer-driven contracts and schema compatibility.
- Test data builders, fixtures, isolation and deterministic tests.
- Concurrency, flaky, performance, load, stress and soak testing.
- Mutation testing, coverage interpretation, TDD, BDD and testing pyramid.

### J. Spring Framework
- IoC, BeanFactory, ApplicationContext, component scanning and bean lifecycle.
- Bean scopes, configuration, profiles, conditional beans and externalized properties.
- Constructor/setter/field injection, qualifiers, primary beans and circular dependencies.
- AOP, proxies, pointcuts, advice and self-invocation limitations.
- Events, validation, conversion, property binding and lifecycle.
- Transactions, propagation, isolation, rollback rules and proxy boundaries.
- Spring MVC lifecycle, filters, interceptors, argument resolvers and exception handling.
- Spring Security filter chain, authentication, authorization, method security and OAuth2 resource server.
- Spring Data abstractions, repositories, pagination and sorting.

### K. Spring Boot and API engineering
- Auto-configuration, starters, conditions, configuration properties and custom starters.
- Startup, profiles, external configuration, secrets and environment strategy.
- REST/HTTP semantics, status codes, idempotency, pagination and API versioning.
- DTO mapping, validation, error contracts, Jackson and OpenAPI.
- Actuator, health/readiness/liveness, metrics, logs, tracing and correlation IDs.
- Graceful shutdown, probes, container deployment and production readiness.
- Spring Boot 3/4 migration and Jakarta namespace compatibility.

### L. Persistence, Hibernate and JPA
- Relational modelling, entity lifecycle, persistence context and dirty checking.
- Entity states, identifiers and generation strategies.
- One-to-one, one-to-many, many-to-one, many-to-many and relationship ownership.
- Fetch types, fetch joins, entity graphs, batch fetching and N+1.
- Cascades, orphan removal and bidirectional relationship consistency.
- JPQL, Criteria, native queries, projections and DTO queries.
- Transactions, isolation, optimistic/pessimistic locking and version columns.
- First/second-level cache, query cache and invalidation.
- Offset/keyset pagination, sorting, streaming and large-result handling.
- Auditing, soft delete, converters, inheritance and composite keys.
- Hibernate SQL diagnostics, JDBC, pooling, batching and prepared statements.
- Flyway/Liquibase, migration ordering, rollback and zero-downtime schema evolution.

### M. Databases and data engineering
- SQL, joins, subqueries, CTEs, window functions and aggregation.
- Normalization, denormalization, keys, constraints and referential integrity.
- Index types, composite/covering indexes, selectivity and maintenance.
- Explain/analyze, query plans, cardinality, slow-query and lock analysis.
- ACID, isolation, MVCC, locks, deadlocks and transaction anomalies.
- PostgreSQL, MySQL and Oracle concepts and operational differences.
- Partitioning, sharding, replication, read replicas and failover.
- Connection pools, transaction timeouts and saturation.
- NoSQL: document, key-value, wide-column, graph and search databases.
- Redis structures, TTL, eviction, persistence and clustering.
- CDC, outbox, schema evolution, backup/restore and OLTP vs analytics.

### N. Microservices and distributed systems
- Monolith, modular monolith, SOA, microservices and decomposition trade-offs.
- Service boundaries, bounded contexts and domain ownership.
- REST/gRPC, async messaging, discovery, API gateway, ingress and service mesh.
- Timeouts, retries, exponential backoff, jitter, circuit breakers and bulkheads.
- Idempotency, deduplication, ordering and at-least-once delivery.
- Distributed transactions, Saga, orchestration/choreography and compensation.
- Transactional outbox/inbox and CDC.
- Event-driven architecture, event sourcing and CQRS trade-offs.
- Consistency models, CAP, quorum, leader election and split-brain.
- Distributed locks, leases, fencing tokens and coordination.
- Rate limiting, load shedding, backpressure and graceful degradation.
- Multi-region, multi-zone, failover, disaster recovery, RPO and RTO.
- Chaos engineering and failure-mode analysis.

### O. Messaging and streaming
- Kafka brokers, topics, partitions, replicas, ISR and controllers.
- Producers, consumers, keys, ordering, consumer groups and rebalancing.
- Offset commits, delivery semantics, idempotent producers and transactions.
- Retention, compaction, replication, schema registry and partition strategy.
- Kafka Streams and stream processing.
- RabbitMQ exchanges, queues, bindings, routing keys, acknowledgements and dead lettering.
- Retry queues, delayed delivery, poison messages and consumer concurrency.
- Messaging security, observability, sizing and Kafka/RabbitMQ/cloud service selection.

### P. Caching and search
- Cache-aside, read-through, write-through, write-behind and refresh-ahead.
- TTL, eviction, invalidation, stampede, penetration and avalanche.
- Local vs distributed cache, consistency and stale data.
- Redis clustering, Sentinel, replication and persistence.
- Search indexing, analyzers, inverted indexes and Elasticsearch/OpenSearch concepts.
- Cache keys, serialization, hot keys and memory sizing.

### Q. Security engineering
- OWASP Top 10, threat modelling and secure coding.
- Authentication/authorization, RBAC/ABAC, least privilege.
- Password hashing, sessions, opaque tokens, JWT, OAuth2, OIDC and SSO.
- TLS, certificates, secrets, key rotation and service identity.
- SQL injection, XSS, CSRF, SSRF, deserialization and path traversal.
- CORS, CSP, secure headers, validation and output encoding.
- Encryption, signing, key management, PII, privacy and audit.
- Dependency vulnerabilities, SBOM and supply-chain controls.

### R. Performance and capacity engineering
- Latency percentiles, throughput, concurrency, saturation and queue depth.
- CPU, heap, allocation, GC, thread and connection pool diagnostics.
- Profiling, flame graphs, JFR and async-profiler concepts.
- Database bottlenecks, indexes, query tuning and lock contention.
- Network latency, payload size, compression and connection reuse.
- Load testing, capacity modelling, Little's Law and bottleneck analysis.
- Horizontal/vertical scaling, autoscaling, caching and backpressure.
- Benchmark methodology, warm-up and regression detection.

### S. Cloud, containers, DevOps and observability
- Linux/process/network basics, DNS, TLS, ports and troubleshooting.
- Docker images/layers, multi-stage builds, non-root containers and health checks.
- Kubernetes pods, deployments, services, ingress, configmaps, secrets and namespaces.
- Requests/limits, probes, HPA, PDB, affinity, scheduling and autoscaling.
- AWS compute, object storage, managed databases, IAM, networking and monitoring.
- CI/CD, artifact promotion, deployment gates, blue/green, canary and rolling releases.
- Terraform, infrastructure as code, GitOps, Jenkins and Argo CD.
- Metrics/logs/traces, SLOs, Prometheus, Grafana, OpenTelemetry, Micrometer and Actuator.
- Incident response, on-call, postmortems and operational readiness.

### T. Architecture and system design
- Requirements, constraints, functional/non-functional requirements and estimation.
- Traffic, storage, bandwidth and capacity calculations.
- API/data design, component and sequence diagrams.
- Scalability, availability, reliability, durability and consistency.
- Load balancing, caching, queues, partitioning, replication and CDN.
- Design exercises: URL shortener, booking, payments, notifications, feeds, chat, file storage, rate limiter and multi-tenant SaaS.
- Multi-AZ/multi-region, disaster recovery, security, privacy, compliance and cost.
- ADRs, trade-off defence, migrations and low-level design.

### U. Engineering leadership
- Technical ownership, estimation, decomposition and delivery planning.
- Code review, mentoring, standards and technical debt.
- Incident command, root-cause analysis and stakeholder communication.
- Cross-team architecture alignment, build-vs-buy and prioritization.
- Migration planning, strangler pattern and modernization.
- Production readiness, reliability ownership and behavioral interviews.

### V. Coding interview problem solving
- Big-O, amortized analysis and complexity reasoning.
- Arrays, strings, hashing, two pointers, sliding window and prefix sums.
- Linked lists, stacks, queues, heaps and monotonic structures.
- Trees, BST, tries, graphs, BFS, DFS, topological sort and shortest paths.
- Sorting, binary search, recursion, backtracking and divide-and-conquer.
- Greedy, dynamic programming, intervals and union-find.
- Bit manipulation, math and probability basics.
- Java implementation details, edge cases, tests, debugging and verbal reasoning.

## Role-specific interview tracks
- Junior Java Developer.
- Java Developer (2–5 years).
- Senior Java Developer (5–8 years).
- Lead Java Engineer / Tech Lead.
- Staff / Principal Backend Engineer.
- Java Architect / Solution Architect.
- Spring Boot Microservices Engineer.
- Platform / Performance Engineer.
- Cloud-native Java Engineer.

Each role path defines expected depth, must-know topics, practical exercises, coding difficulty, design expectations and behavioral competencies.

## Assessment formats
Single/multi-select, true/false, output prediction, code tracing, bug fixing, short verbal answer, coding with test cases, SQL, query-plan interpretation, architecture rubric, timed mock, adaptive follow-ups, weak-area practice, spaced repetition, daily challenge and incorrect-answer retry.

## Lesson quality gate
Every published lesson requires objectives, prerequisites, accurate version applicability, tested code or labelled pseudocode, practical use cases, trade-offs, mistakes, concise interview answer, detailed explanation, follow-ups, assessment items, source attribution, reviewer approval and revision history.

This is a coverage blueprint, not authored lesson content. User-original notes remain primary when supplied; external material must be attributed and reviewed.
