# Advanced Persistence: JPA, Hibernate, SQL, Transactions and Data Engineering

> **Status:** Draft for technical review; not published.
> **Audience:** Senior Java / Tech Lead / Architect
> **Baseline:** Jakarta Persistence 3.x, Hibernate ORM 6.x, PostgreSQL concepts; behavior is provider/database-specific where noted.
> **Prerequisites:** SQL, Java object model, Spring transactions and basic database modeling.

## Outcomes

Design persistence models that preserve domain invariants; explain the persistence context and entity lifecycle; control flush, dirty checking and fetching; diagnose N+1 and inefficient query plans; select pagination and locking strategies; reason about isolation/deadlocks; and perform large migrations safely.

## Interview-ready summary

JPA is a persistence specification, while Hibernate is a provider implementing it. The persistence context is an identity map and unit-of-work: within a context, an entity identity maps to one managed instance, and changes are tracked for synchronization. SQL is typically emitted during flush, not necessarily at the line where an entity changes. Performance problems often arise from fetch-plan mismatch, N+1 queries, poor indexes, excessive entity hydration or long transactions. I diagnose from generated SQL, query plans, database statistics and connection-pool metrics. For high-volume reads I use projections and keyset pagination; for concurrent writes I choose optimistic or pessimistic locking based on contention and correctness requirements. Bulk migrations need bounded batches, resumability, idempotency and observability.

## 1. Relational modeling and schema design

### Entities are not database tables by definition

An entity models identity and lifecycle within the domain. The relational schema models durable constraints and relationships. Do not let ORM convenience replace database integrity.

- Use primary keys with stable identity.
- Enforce uniqueness, foreign keys, not-null and check constraints in the database.
- Normalize to reduce update anomalies; denormalize only for a measured read/scale requirement with a consistency plan.
- Model many-to-many relationships with an explicit association entity when the relationship carries attributes, lifecycle or audit data.
- Avoid unbounded text/blob fields in frequently accessed rows when they distort I/O patterns.

### Entity identity and equals/hashCode

Entity identity can be difficult before generated IDs exist. Avoid naive equals/hashCode implementations based on mutable fields or a null generated ID. Document the identity strategy and test behavior while transient, managed and detached. Be especially careful placing mutable entities in hash-based collections.

## 2. Persistence context and entity states

Common states:
- **Transient/new:** not associated with a persistence context.
- **Managed:** tracked by the context; changes may be synchronized.
- **Detached:** previously managed but no longer tracked.
- **Removed:** scheduled for deletion.

The persistence context maintains identity: repeated loading of the same entity key within one context normally returns the same managed instance. It also acts as a unit-of-work, tracking changes for flush.

### Dirty checking

Hibernate can detect changes to managed entities and generate updates at flush. This is convenient, but broad entity loading and mutation can create expensive tracking and SQL.

Prefer narrow transaction scopes and avoid loading large graphs merely to update a few columns. For bulk updates, consider a direct JPQL/SQL operation, while understanding that bulk DML bypasses normal per-entity lifecycle/dirty checking and can leave already-managed state stale; clear or refresh appropriately.

### Flush vs commit

Flush synchronizes pending persistence-context changes with the database. Commit finalizes the transaction. Flush may occur:
- explicitly via EntityManager.flush()
- before transaction commit
- under provider-specific/query-triggered conditions, such as AUTO flush before a query whose result could be affected.

Flush does not mean the transaction is committed or durable to other transactions. Constraint violations may surface at flush or commit rather than at the original persist call.

## 3. Mapping and relationship ownership

JPA relationship mappings require clear ownership:
- Many-to-one commonly owns the foreign-key column.
- One-to-many is often inverse via mappedBy.
- Many-to-many uses a join table, but an association entity is often better for domain-rich relationships.

Keep cascades intentional. Cascade REMOVE across shared/reference data can delete objects unexpectedly. Orphan removal models private child lifecycle, not a general cleanup mechanism.

Avoid bidirectional object graphs that recurse through JSON serialization. Map entities to DTOs and define explicit API representations.

## 4. Fetch strategies and N+1

### Lazy vs eager

Fetch type is a default mapping hint, not a universal runtime query plan. EAGER may still produce secondary queries depending on provider/query shape; LAZY can trigger extra selects when traversed.

N+1 occurs when a query loads N parent rows and then association access triggers one query per parent. Detect by SQL count, tracing and query logs—not just code inspection.

### Fetch-plan tools

- JPQL fetch join for a specific query path.
- Entity graphs for declarative fetch requirements.
- DTO projections for read models.
- Batch fetching to reduce repeated selects in supported providers.
- Explicitly designed secondary queries when one giant join would multiply rows.

Do not blindly fetch-join multiple to-many relationships: cartesian row multiplication can balloon result size, duplicate parent materialization and interact badly with pagination.

## 5. Query optimization and SQL plans

### Index fundamentals

Indexes improve selected reads but consume storage and slow writes. Choose index columns based on predicates, join keys, ordering and selectivity.

- Composite index column order matters; match common left-prefix predicates and query shapes.
- Avoid indexing every column.
- Include uniqueness where it is a business invariant.
- Consider covering/index-only access only after verifying the engine's plan and visibility/statistics requirements.

### Read the execution plan

Use database-native explain tools (e.g., PostgreSQL EXPLAIN / EXPLAIN ANALYZE) in a safe environment. Compare estimated and actual rows, scan type, join strategy, sort, buffers and execution time. EXPLAIN ANALYZE executes the query; do not run expensive production statements casually.

ORM query tuning sequence:
1. Capture the actual SQL and bind values safely.
2. Reproduce representative cardinality.
3. Inspect the database plan and statistics.
4. Fix query shape, indexes or data access pattern.
5. Reduce selected columns/rows.
6. Measure end-to-end latency, not only database execution.

## 6. Pagination at scale

### Offset pagination

`LIMIT/OFFSET` is simple and supports page-number navigation, but deep offsets can require scanning/skipping many rows and concurrent inserts/deletes can shift page contents.

Always use a deterministic order with a unique tie-breaker; pagination without ORDER BY is not stable.

### Keyset (seek) pagination

For a stable order such as (created_at, id), seek after the last observed tuple:

```sql
SELECT id, created_at, title
FROM lesson
WHERE (created_at, id) > (:lastCreatedAt, :lastId)
ORDER BY created_at, id
LIMIT :pageSize;
```

This avoids deep offset scanning when a suitable index supports the predicate/order. Handle null sort values and direction explicitly; cursor tokens should encode validated, versioned sort state and must not expose secrets.

Keyset pagination is not a drop-in for arbitrary page-number jumps. It is ideal for feeds, exports and sequential browsing.

## 7. Transactions, isolation and locking

### Isolation anomalies

Understand:
- Dirty read: observing uncommitted data.
- Non-repeatable read: same row returns changed values during a transaction.
- Phantom: repeated predicate query returns a changed set.
- Lost update: concurrent writes overwrite one another without detection.
- Write skew: concurrent transactions preserve individual checks but violate a cross-row invariant.

Actual behavior depends on the database's isolation implementation, MVCC and locking.

### Optimistic locking

Use a version column with @Version when conflicts are expected to be relatively rare. The update checks the version; stale writes fail and the application must retry or report a conflict according to domain semantics.

### Pessimistic locking

Use database locks when contention/correctness justifies blocking. Define lock scope, timeout and ordering. Keep the transaction short; do not hold row locks while waiting for remote services or user interaction.

### Deadlocks

A deadlock is a cycle in lock dependencies. The database typically aborts one participant. Reduce risk by:
- Consistent lock ordering.
- Short transactions.
- Appropriate indexes (reducing scanned/locked rows).
- Avoiding unnecessary lock scope.
- Retry only known transient deadlock/serialization failures, with bounded backoff and idempotent transaction logic.

## 8. Connection pools and transaction boundaries

A connection pool is a finite concurrency resource. Pool exhaustion can indicate slow queries, leaked connections, long transactions, too much request concurrency or pool sizing mismatch.

Monitor active/idle/pending connections, acquisition latency, transaction duration and query latency. Increasing pool size without database capacity analysis can worsen contention.

Never assume a Spring transaction automatically follows work to another thread or spans a remote HTTP request, Kafka broker and database as one atomic transaction. Use outbox/saga patterns for distributed workflows.

## 9. Bulk data and migration engineering

For high-volume updates/imports:
- Use bounded batches and commit checkpoints.
- Define idempotent keys and restart behavior.
- Control persistence-context growth with periodic flush/clear when using entity batches.
- Use database-native bulk operations when appropriate.
- Track throughput, failures, lag and retry count.
- Validate row counts and business invariants before/after.
- Plan schema changes for rolling deployments: expand, deploy compatible code, backfill, switch, contract.
- Avoid destructive schema changes while old application versions may still run.

A migration must be resumable and observable, not a one-off script that assumes uninterrupted execution.

## 10. Caching and consistency

### First-level cache

The persistence context is the first-level cache, scoped to its context/session. It guarantees identity within that context, not cross-request cache freshness.

### Second-level/query cache

Provider-level caches require explicit consistency and invalidation understanding. Do not enable them blindly for mutable data or assume a query cache is a general replacement for database indexing.

### Application cache (e.g. Redis)

Choose cache-aside, write-through or other strategy based on read/write patterns. Define TTL, invalidation, stale-data tolerance, stampede protection and key versioning. Cache correctness is a domain contract.

## 11. Production debugging playbook

| Symptom | Investigate |
|---|---|
| Endpoint issues hundreds of SQL statements | N+1, lazy traversal, repository loops |
| Slow page 5000 | Offset pagination, missing/order index |
| High DB CPU | Plan regression, cardinality, missing index, inefficient joins |
| Pool pending grows | Slow query, long transaction, leaked connection, concurrency |
| Optimistic lock failures spike | Hot rows, retry storm, conflict policy |
| Migration slows production | Batch size, locks, WAL/redo, index build, throttling |
| Heap grows during import | Persistence-context retention, large collections, batch clear |
| Stale cache after update | Invalidation race, TTL policy, multi-instance consistency |

## 12. Exercises

1. Implement a bidirectional Order/OrderLine mapping; explain ownership, cascade and orphan removal.
2. Reproduce N+1 and fix it using a DTO projection or targeted fetch plan; compare query count and rows transferred.
3. Design an index for a tenant-scoped, time-ordered search and justify column order using a plan.
4. Convert deep offset pagination into keyset pagination with a stable cursor and tie-breaker.
5. Reproduce optimistic locking conflict with two transactions and design user-facing retry behavior.
6. Design a restartable 10-million-row migration with checkpoints, idempotency and deployment compatibility.

## 13. Senior / architect interview questions

- Explain persistence context identity map and unit-of-work behavior.
- When does Hibernate issue SQL? Distinguish persist, flush and commit.
- How does dirty checking work and what costs can it introduce?
- Why can EAGER still lead to multiple queries?
- Diagnose and prevent N+1 without creating cartesian explosion.
- Compare offset and keyset pagination; what are their API trade-offs?
- How would you handle lost updates and write skew?
- When choose optimistic vs pessimistic locking?
- How do you diagnose pool exhaustion?
- Why can bulk JPQL update make managed entities stale?
- Design a zero-downtime schema migration across application versions.
- How would you tune a query with a poor cardinality estimate?
- When is a cache harmful or incorrect?
- How do you process millions of records without exhausting heap or holding locks?

## References

- Jakarta Persistence specification: https://jakarta.ee/specifications/persistence/
- Hibernate ORM documentation: https://hibernate.org/orm/documentation/
- PostgreSQL query planning: https://www.postgresql.org/docs/current/using-explain.html
- PostgreSQL indexes: https://www.postgresql.org/docs/current/indexes.html
- Spring Data JPA reference: https://docs.spring.io/spring-data/jpa/reference/

**Editorial gate:** Compile/execute examples against the repository's chosen ORM/database versions; review SQL and mapping behavior; keep content in draft until approved.