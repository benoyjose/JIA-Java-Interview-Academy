# Senior System Design: Requirements, Capacity, Reliability and Multi-Region Architecture

> **Status:** Draft for technical review; not published.
> **Audience:** Senior / Staff / Principal / Architect interviews
> **Approach:** Technology-agnostic, with implementation trade-offs grounded in backend engineering.

## Outcomes

Drive a design interview from ambiguous requirements to a defensible architecture; quantify scale; select data and communication patterns; reason about availability, consistency, latency and cost; design multi-region recovery; and communicate decisions through diagrams, APIs, data models, failure paths and measurable SLOs.

## A repeatable interview framework

1. **Clarify the problem:** users, workflows, constraints, exclusions and success criteria.
2. **Define functional requirements:** core operations and state transitions.
3. **Define non-functional requirements:** throughput, latency, availability, durability, privacy, consistency and cost.
4. **Estimate scale:** DAU/MAU, peak requests, read/write ratio, payload size, retention and growth.
5. **Propose APIs and data model:** invariants, indexes, ownership and access patterns.
6. **Draw the high-level design:** client, edge, services, storage, messaging and external dependencies.
7. **Deep dive the bottleneck:** partitioning, cache, consistency, queues, concurrency or hot paths.
8. **Walk failure scenarios:** dependency outage, duplicate request, data loss, region failure, overload.
9. **Discuss operations/security/cost:** SLOs, dashboards, deployment, threat model and cost drivers.
10. **Summarize trade-offs and evolution:** what is simple now, what changes at 10x scale, and trigger conditions.

A design answer is not complete when boxes are drawn. It must show data ownership, request/event flow, failure handling and the reason each major choice fits the requirements.

## 1. Requirements and constraints

Separate must-have behavior from quality attributes. Ask:
- Who is the actor and what is the primary workflow?
- Is the operation synchronous or can it complete asynchronously?
- What is the consistency requirement?
- What is the acceptable p95/p99 latency?
- What is the availability target and failure domain?
- Is data regional, global, sensitive or regulated?
- What are retention, deletion and audit requirements?
- What is the expected growth and budget?

Avoid designing for invented requirements. State assumptions explicitly and adjust if the interviewer changes them.

## 2. Capacity estimation

Use round numbers and show assumptions. Example for a service with 10 million daily active users:

- Assume 20 requests/user/day → 200 million requests/day.
- Average RPS ≈ 200M / 86,400 ≈ 2,315.
- If peak is 5x average → roughly 11.6k peak RPS.
- If 10% are writes → about 1.16k peak writes/s.
- If each stored event is 1 KB and retained 30 days → estimate raw storage, then include indexes, replication and overhead separately.

Do not overfit precision. Capacity estimates guide architectural constraints, not exact procurement.

### Little's Law

For a stable system, average concurrency (L = lambda W), where (lambda) is throughput and (W) is average time in system. If 2,000 requests/s spend 0.25 s in service, average in-flight requests are about 500. This helps reason about worker/connection concurrency, but does not replace tail-latency and saturation measurements.

### Storage and bandwidth

Estimate:
- Data generated/day × retention.
- Replication and backup multiplier.
- Index and metadata overhead.
- Peak ingress/egress and fan-out.
- Rebuild/catch-up throughput after outage.

## 3. API and data model

Define the core API before choosing infrastructure. Include idempotency, pagination, versioning and error semantics.

Data design follows access patterns and invariants:
- Relational DB for transactions, constraints and structured relationships.
- Document store when aggregate access and schema flexibility justify it.
- Key-value store for predictable key access and high-scale lookup.
- Search engine for full-text/ranking, not as source of truth by default.
- Object storage for large immutable blobs.
- Time-series/analytics store for appropriate telemetry/query patterns.

Choose the authoritative owner for each invariant. A read model or cache is not authoritative unless the domain explicitly makes it so.

## 4. Scaling patterns

### Stateless horizontal scaling

Keep request-serving instances stateless where practical; store durable state externally. Use health-aware load balancing, graceful shutdown and bounded per-instance resources.

Autoscaling needs a meaningful signal and startup time. CPU alone may not reflect queue or DB saturation. Scale on queue age, concurrency or request load where appropriate, but ensure downstream capacity.

### Caching

Cache-aside:
1. Read cache.
2. On miss, read source.
3. Populate with TTL.
4. Invalidate/update after authoritative write according to policy.

Risks: stale reads, cache stampede, hot keys, invalidation race, oversized values and cold-start load. Use jittered TTL, request coalescing, bounded concurrency and stale-while-revalidate only where semantics allow.

### Load balancing and CDN

Use CDN for cacheable static/edge content and reduce origin traffic. Load balancing distributes requests but does not make a stateful service scalable if every request depends on one session/database lock.

### Queue and backpressure

A queue absorbs bursts and decouples producer/consumer speed, but adds latency and eventual consistency. Bound queue size, monitor oldest age, and define overload policy. Queueing does not eliminate capacity limits.

## 5. Database scale and partitioning

### Read replicas

Replicas improve read capacity and isolate some workloads, but introduce replication lag. Route read-after-write queries to primary or use a consistency token/replication position where supported.

### Sharding

Shard when a single node cannot meet capacity or isolation needs. Choose a shard key with:
- High cardinality and even distribution.
- Query locality.
- Stable mapping and manageable rebalancing.
- No excessive hot partition.

Sharding complicates cross-shard transactions, global ordering, unique constraints, joins and operations. Prefer vertical optimization/indexing/read replicas before sharding unless requirements justify it.

### Denormalization

Denormalize to optimize measured read paths; define the source of truth and update/rebuild strategy. Duplicate data without ownership and consistency rules creates permanent drift.

## 6. Reliability and availability

### SLOs and error budgets

Define service-level indicators such as request success, p99 latency, freshness and durability. An SLO is a target over a window; error budget is the allowed failure portion. Use burn rate to detect rapid or sustained budget consumption.

Availability depends on dependencies and correlated failure. Multiplying component availabilities assumes independence, which often does not hold in shared regions, networks or control planes.

### Redundancy and failover

Redundancy only helps when failure domains are independent and failover is tested. Include:
- Health detection and routing.
- Data replication lag.
- Recovery automation and runbooks.
- Capacity during failover.
- Split-brain prevention.
- Reconciliation and consistency repair.

## 7. Multi-region / multi-data-center design

### Active-passive

One region is authoritative for writes; a standby receives replication and takes over after failure.

Advantages: simpler consistency and write ownership.
Costs: standby capacity, failover delay, replication lag and promotion complexity.

### Active-active

Multiple regions serve writes. Requires conflict resolution, partitioned ownership or coordination. It is not automatically more available if global dependencies or consensus paths become critical.

Questions to answer:
- Which region owns a user's/entity's writes?
- How are concurrent updates reconciled?
- Can a region operate disconnected?
- What happens to uniqueness and payment/inventory invariants?
- How is failback performed after the primary recovers?

### RPO and RTO

- **RPO:** maximum acceptable data loss measured in time.
- **RTO:** target time to restore service.

These are product and business decisions. Replication mode, backup cadence, recovery testing and failover design must meet them.

## 8. Rate limiting, quotas and graceful degradation

Rate limiting protects shared resources and fairness. Common algorithms:
- Fixed window: simple, boundary bursts.
- Sliding window: smoother but more state/cost.
- Token bucket: supports a defined burst plus sustained rate.
- Leaky bucket: smooths output rate.

Define key scope (user, tenant, IP, API key), distributed coordination, fail-open/fail-closed behavior and retry response.

Graceful degradation must preserve safety: serving stale product descriptions may be acceptable; accepting a payment without authoritative confirmation is not.

## 9. Security and privacy

Threat model:
- Authentication and authorization boundaries.
- Tenant isolation.
- Abuse and rate limits.
- Data classification and encryption.
- Secret and key rotation.
- Audit and retention.
- Dependency and supply-chain risk.
- Least privilege for service identities.

Do not expose internal IDs, tokens or sensitive fields unnecessarily. Security must be considered in API, data, operations and recovery design.

## 10. Design case: notification platform

Requirements: millions of users, multi-channel delivery, preference management, retry, scheduling and provider failures.

Architecture:
- API writes notification intent and outbox record.
- Scheduler/dispatcher creates channel-specific jobs.
- Queue partitions by tenant/user or notification ID according to ordering requirement.
- Workers apply rate limits and provider-specific bulkheads.
- Status store tracks queued/sent/failed; provider callbacks update delivery state.
- Retry with backoff; permanent failures route to DLQ.
- User preferences and consent are checked at dispatch, not only at request time.
- Idempotency prevents duplicate sends on retry.
- Metrics include queue age, send success, provider latency, retries, cost and opt-out compliance.

Trade-offs: delivery is generally at-least-once; true duplicate-free external delivery depends on provider idempotency and callback semantics.

## 11. Design case: high-scale booking

- Inventory is authoritative and uses atomic reservation/unique constraints.
- Availability browsing may use a cached read model, but final booking revalidates.
- Booking state machine: HOLD → CONFIRMED / EXPIRED / CANCELLED.
- Payment integrates through a saga with idempotency and compensation.
- Reservation TTL and cleanup are explicit.
- Multi-region writes require ownership/coordination to prevent oversell.
- SLOs include booking success, payment outcome resolution, hold expiration and reconciliation backlog.

## 12. Common design mistakes

1. Choosing Kafka, Kubernetes or NoSQL before requirements.
2. Treating average RPS as peak.
3. Claiming horizontal scaling without considering DB/pool limits.
4. Using cache as authoritative state without consistency design.
5. Saying “exactly once” without scope and failure analysis.
6. Ignoring hot keys and partition skew.
7. Designing active-active without conflict/ownership rules.
8. Omitting RPO/RTO and recovery capacity.
9. Adding microservices for every entity.
10. Failing to explain why a component exists and how it fails.

## 13. Exercises

1. Estimate capacity and storage for a video metadata service at 5x growth.
2. Design a URL shortener: key generation, collision, redirect latency, abuse and analytics.
3. Design a notification service with per-user ordering and provider quotas.
4. Design a booking system with oversell prevention and payment saga.
5. Plan a regional failover with explicit RPO/RTO, data authority and failback.
6. Review an architecture with 12 synchronous service hops and propose a latency/failure budget.

## 14. Senior / architect interview prompts

- How do you structure requirements and identify hidden constraints?
- Estimate peak RPS, storage and network traffic from incomplete inputs.
- Explain the trade-off between consistency and availability in a concrete operation.
- When does a modular monolith beat microservices?
- How do you select a partition key and identify hot shards?
- What is the difference between replication and backup?
- Design active-passive and active-active recovery; define RPO/RTO.
- How do you protect a system during dependency failure and overload?
- What should be cached, and how is invalidation handled?
- How do you measure and communicate architectural risk and cost?

## References

- Google SRE Workbook: https://sre.google/workbook/table-of-contents/
- AWS Well-Architected Framework: https://docs.aws.amazon.com/wellarchitected/latest/framework/welcome.html
- Azure Architecture Center: https://learn.microsoft.com/en-us/azure/architecture/
- Designing Data-Intensive Applications (reference for deeper reading; not a source for copied lesson text).

**Editorial gate:** Review all capacity assumptions as examples, validate diagrams and ensure RPO/RTO, consistency and failover statements are appropriately qualified before publication.