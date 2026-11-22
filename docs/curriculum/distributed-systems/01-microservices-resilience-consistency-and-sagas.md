# Advanced Backend Architecture: Microservices and Distributed Systems

> **Status:** Draft for technical review; not published.
> **Audience:** Senior Engineer, Tech Lead, Staff/Principal Architect
> **Scope:** Technology-neutral distributed-system foundations with Java/Spring examples; verify library-specific APIs against deployed versions.

## Learning outcomes

Decompose services around business capabilities and ownership; reason about partial failure, latency and consistency; design reliable synchronous and asynchronous interactions; implement idempotency, outbox/inbox, saga and retry controls; understand partitioning, ordering and distributed coordination; and explain operational trade-offs with measurable SLOs.

## Spoken interview answer

Microservices are independently deployable services aligned to cohesive business capabilities and data ownership. Their main cost is distributed-system complexity: network calls fail partially, latency compounds, consistency becomes explicit and operations require observability and automation. I start with domain boundaries and non-functional requirements, not a service-count target. For synchronous calls I enforce deadlines, bounded retries, circuit breaking and bulkheads. For asynchronous workflows I use durable events, idempotent consumers, outbox/inbox patterns and explicit eventual-consistency states. I avoid distributed transactions where possible and model business workflows as sagas with compensations. The design is evaluated against availability, latency, recovery, data correctness and operating cost.

## 1. Decomposition and service boundaries

### Start with business capabilities

A service should own a cohesive capability, vocabulary, policy and data lifecycle. Decompose around bounded contexts and team ownership rather than technical layers such as “controller service” and “database service.”

A boundary is weak when:
- Multiple services routinely update the same tables.
- A single user action requires many synchronous hops.
- Domain rules are duplicated or inconsistent.
- Teams cannot deploy independently because of tight release coupling.
- The service is too small to own a meaningful capability.

A modular monolith can be the better first architecture. It preserves module boundaries and simpler transactions while the domain and scale are uncertain. Extract a service when independent deployment, ownership, scaling or fault isolation justifies the network and operational cost.

### Data ownership

Each service owns its authoritative data and schema. Other services access it through APIs/events, not shared direct table writes. Shared database infrastructure can be acceptable; shared ownership and cross-service write coupling are the problem.

Cross-service reporting may use read models, CDC or a data platform, but freshness and lineage must be documented.

## 2. Distributed failure model

A remote call can:
- Fail before reaching the server.
- Execute but lose the response.
- Return after the caller's deadline.
- Be retried and execute more than once.
- Succeed while a downstream dependency later fails.
- Stall due to DNS, connection pool, queue or thread exhaustion.

A timeout does not prove the remote operation did not happen. This is why retry safety and idempotency matter.

### Latency compounding

If a request synchronously calls multiple dependencies, total latency includes network, queueing and service time. A chain with individually acceptable p99 latencies can have an unacceptable end-to-end tail. Reduce fan-out, use parallelism only when dependencies are independent, set an overall deadline and define partial-result behavior.

### Failure budgets

Every dependency consumes part of the request's latency and availability budget. Define:
- Deadline and timeout for each hop.
- Maximum concurrency and queue size.
- Retry budget.
- Fallback or degraded behavior.
- Telemetry and alert thresholds.

## 3. Resilience patterns

### Timeouts and deadlines

Set connect, read and overall request deadlines. Propagate remaining deadline where protocols support it. A downstream timeout should be shorter than the caller's remaining budget; otherwise the caller waits after its own request is already expired.

Do not rely on unbounded defaults.

### Retry with backoff and jitter

Retry only transient failures and operations safe to repeat. Use exponential backoff with jitter, bounded attempts and a total retry budget. Retries at multiple layers multiply load; establish one intentional retry owner.

Do not retry validation errors, authorization failures or deterministic business conflicts. Respect server Retry-After where applicable.

### Circuit breaker

A circuit breaker temporarily rejects calls after a failure threshold, allowing recovery and protecting resources. It is not a substitute for timeouts. Tune rolling window, minimum volume, open duration and half-open probes to workload; monitor state transitions.

### Bulkhead and backpressure

Isolate dependency pools or concurrency limits so one slow downstream does not exhaust all request workers. Bound queues and reject/slow work explicitly when saturated. An unbounded queue converts overload into latency and memory failure.

### Fallback

Fallback must preserve domain correctness. Returning stale cached inventory, a default price or “success” for a failed payment may be unsafe. Define what can degrade, for how long, and how the user is informed.

## 4. Idempotency and exactly-once claims

At-least-once delivery and client retries can duplicate operations. Idempotency means repeating the same logical request has the same effect.

For a payment/order command:
1. Client supplies a stable idempotency key scoped to user/operation.
2. Server validates and stores key plus request fingerprint.
3. Key and business effect are committed atomically.
4. Repeated same-key/same-payload requests return the recorded result.
5. Same key with different payload is rejected.
6. Retention duration and replay semantics are documented.

“Exactly once” is usually a scoped processing guarantee, not a magical end-to-end property across HTTP, database and broker. Build deduplication and idempotent state transitions.

## 5. Event-driven communication

### Events vs commands

A command asks a specific owner to perform an action. An event states that a fact occurred. Events should be named in past tense and carry stable identifiers, schema version, occurred-at time, producer and correlation/causation metadata.

Avoid publishing internal database entities as event contracts. Design for compatibility and minimal necessary data.

### Outbox pattern

A dual-write failure occurs when the service commits its database change but crashes before publishing the message, or publishes then rolls back.

The transactional outbox stores the business update and event record in one local transaction. A relay publishes pending records and marks progress. Delivery can be duplicated, so consumers must be idempotent.

Operational concerns:
- Relay polling/CDC strategy and lag.
- Ordering key and aggregate sequence.
- Retry and poison-message policy.
- Cleanup/retention.
- Monitoring unpublished backlog.

### Inbox / deduplication

A consumer records processed event IDs in the same transaction as its business effect. A unique constraint prevents duplicate application. The deduplication scope and retention must exceed the expected replay window.

## 6. Sagas and eventual consistency

A saga is a sequence of local transactions with compensating actions for business-level recovery. It does not roll back history like a single ACID transaction.

Example order flow:
1. Create order in PENDING.
2. Reserve inventory.
3. Authorize payment.
4. Confirm order.
5. On failure, release inventory and/or void payment, then mark the order appropriately.

Compensation is a new domain action and may fail; it needs retries, observability and manual repair paths. Model states explicitly and ensure repeated commands are safe.

### Orchestration vs choreography

- **Orchestration:** central workflow coordinator tracks state and invokes participants; clearer visibility and control, but coordinator is a component to operate.
- **Choreography:** participants react to events; less central control but harder to understand global flow, detect cycles and debug.

Choose based on workflow complexity, audit needs and team ownership—not ideology.

## 7. Consistency and data design

### Strong vs eventual consistency

Strong consistency simplifies certain invariants but may require coordination and reduce availability/latency under failures. Eventual consistency permits replicas/read models to lag; the product must tolerate and expose that lag.

Use a consistency requirement per operation:
- Payment authorization and inventory reservation may need guarded state transitions.
- Search/feed/analytics can often tolerate stale read models.
- User-facing status should explain pending asynchronous outcomes.

Avoid distributed reads/writes merely to mimic one database transaction. Keep invariants inside one owner where possible.

### CQRS and read models

CQRS separates command and query models when their needs differ materially. It adds synchronization, schema, rebuild and operational costs. Do not introduce it for every CRUD service.

Read models may be rebuilt from authoritative events or snapshots; define replay, checkpoint and schema-evolution behavior.

## 8. Ordering, partitioning and concurrency

Ordering is generally guaranteed only within a defined scope (for example, a partition or aggregate key), not globally. Select partition keys to preserve the required ordering and avoid hot partitions.

Sequence numbers/version fields can detect stale or out-of-order events. Consumers should handle duplicates, gaps and replay.

Distributed locks are not a default solution. A lease can expire while a paused client continues operating; fencing tokens or database constraints may be needed to prevent stale lock holders from corrupting state.

## 9. Service discovery, gateway and configuration

A gateway can centralize routing, authentication integration, rate limits and edge concerns, but avoid placing all business orchestration there.

Service discovery and configuration must handle:
- Dynamic instances and health.
- DNS/cache behavior.
- Secret rotation.
- Environment promotion.
- Startup dependencies and graceful degradation.

Do not couple application correctness to a single mutable config service without a fallback/startup policy.

## 10. Security between services

- Authenticate service identities; do not trust network location alone.
- Use TLS/mTLS where justified by threat model and platform.
- Apply least-privilege scopes and audience validation.
- Propagate user identity/authorization context carefully; never blindly trust client-supplied headers.
- Keep secrets out of logs, traces and event payloads.
- Enforce tenant boundaries at each service's data access layer.
- Validate event producers and schemas.

## 11. Observability and operations

Every request/workflow should have trace/correlation context across HTTP and messaging. Track:
- Request rate, error rate, latency percentiles.
- Dependency timeouts, retries and breaker states.
- Queue depth, consumer lag and oldest-message age.
- Outbox backlog and saga state age.
- Saturation: threads, connections, CPU, memory and queues.
- Business outcomes: orders stuck, payments pending, compensations failed.

Alert on user-impacting symptoms and SLO burn, not every internal exception.

## 12. Production failure scenarios

### Duplicate payment command
Idempotency key, durable result, unique constraint, reconcile provider transaction IDs.

### Inventory reserved but order not confirmed
Saga state, timeout/compensation, retry-safe release and reconciliation job.

### Event published twice
Consumer inbox/deduplication and idempotent state transition.

### Downstream slow causes thread exhaustion
Deadlines, bulkhead, bounded queue, circuit breaker and load shedding; inspect connection-pool and worker saturation.

### Read model is stale
Expose last-updated/version, monitor lag, retry/rebuild path and define whether user flow can proceed.

### One region unavailable
Define failover authority, data replication lag, RPO/RTO, write ownership and split-brain prevention before claiming active-active.

## 13. Architecture exercise: booking platform

Requirements: prevent double booking, support high read volume, tolerate payment delays, handle cancellation and recover from region outage.

A strong design answer:
1. Clarify booking hold duration, inventory granularity, payment semantics and RPO/RTO.
2. Keep inventory reservation as an authoritative transactional owner.
3. Use a unique/conditional database constraint or atomic reservation to prevent oversell.
4. Create booking in PENDING with idempotency key.
5. Coordinate payment via saga; hold expires and compensation releases inventory.
6. Publish state changes via outbox.
7. Use cached/search read models for availability display but validate at reservation.
8. Define queue/backpressure and payment-provider timeout behavior.
9. Instrument stuck states, reservation expiry, payment reconciliation and duplicate attempts.
10. Explain trade-offs and failure paths rather than only drawing components.

## 14. Exercises

1. Design idempotent POST /orders with retry/replay behavior and schema.
2. Implement an outbox table, relay and consumer inbox; test crash after publish before acknowledgement.
3. Model a payment/inventory saga with success, timeout, compensation failure and manual repair.
4. Calculate a latency budget for a fan-out endpoint and define graceful partial response behavior.
5. Choose Kafka partition key for per-order ordering while avoiding hot-key skew.
6. Design region failover with explicit data authority, RPO/RTO and split-brain protections.

## 15. Senior / architect interview questions

- When is a modular monolith preferable to microservices?
- How do you select service boundaries and data ownership?
- Why is a timeout an ambiguous outcome?
- How do retries amplify overload and how do you prevent retry storms?
- Compare circuit breaker, bulkhead and backpressure.
- Design end-to-end idempotency for a payment workflow.
- Explain transactional outbox and consumer inbox.
- How does a saga differ from distributed ACID transactions?
- Orchestration vs choreography: what changes operationally?
- How do you preserve ordering without global serialization?
- What consistency guarantees does the user actually need?
- How would you recover a stuck saga or poisoned event?
- Define multi-region RPO/RTO and prevent split brain.
- What metrics prove a service is healthy and its workflow is progressing?

## References

- Microsoft Cloud Design Patterns (retry, circuit breaker, bulkhead): https://learn.microsoft.com/en-us/azure/architecture/patterns/
- AWS transactional outbox guidance: https://docs.aws.amazon.com/prescriptive-guidance/latest/cloud-design-patterns/transactional-outbox.html
- Google SRE book, distributed systems and reliability: https://sre.google/sre-book/table-of-contents/
- OpenTelemetry concepts: https://opentelemetry.io/docs/concepts/

**Editorial gate:** Validate diagrams/examples against chosen messaging and resilience libraries; review all failure and compensation semantics before publication.