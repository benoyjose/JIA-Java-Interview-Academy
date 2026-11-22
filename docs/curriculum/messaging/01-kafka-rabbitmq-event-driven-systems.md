# Advanced Messaging: Apache Kafka and RabbitMQ

> **Status:** Draft for technical review; not published.
> **Audience:** Senior Backend Engineer / Tech Lead / Architect
> **Scope:** Messaging semantics and operations; verify broker/client feature behavior for the deployed versions.

## Learning outcomes

Choose messaging technology by delivery, ordering, routing, replay and operational requirements; explain Kafka's log/partition/consumer-group model and RabbitMQ's exchange/queue model; design durable producer/consumer flows; handle duplicates, retries, poison messages and schema evolution; and operate messaging systems using lag, queue age, throughput and failure telemetry.

## Interview-ready summary

Kafka is a partitioned, replicated append-only log optimized for durable event streams, replay and consumer-controlled offsets. RabbitMQ is a broker with exchanges, routing rules and queues suited to flexible work distribution and acknowledgement-driven delivery. Neither makes application side effects exactly-once by default. Correct systems use stable event IDs, idempotent processing, explicit retry/DLQ policy, schema compatibility and bounded concurrency. I select based on ordering scope, replay, routing, workload, latency, retention and operational capabilities—not popularity.

## 1. Messaging vocabulary

- **Producer:** publishes records/messages.
- **Broker:** stores/routes messages and coordinates delivery.
- **Consumer:** processes records/messages.
- **Acknowledgement:** indicates broker/consumer progress according to protocol.
- **Delivery guarantee:** at-most-once, at-least-once or a scoped exactly-once mechanism.
- **Ordering:** guarantee within a defined key/queue/partition boundary.
- **Retention:** how long data remains available independent of consumption.
- **Backpressure:** limits work when producers outpace consumers.
- **DLQ:** destination for messages requiring isolation, diagnosis or manual repair.

Distinguish broker delivery from the atomicity of the consumer's database side effect.

## 2. Kafka architecture

### Topics, partitions and replicas

A topic is divided into partitions. Each partition is an ordered log with offsets. Partitions are distributed across brokers and may have replicas; a leader handles writes/reads under the configured protocol and followers replicate.

A partition is the unit of ordering and consumer parallelism. A single partition cannot be consumed in parallel by multiple members of the same consumer group at the same time, though different groups independently read the log.

Partition count affects throughput, parallelism, key distribution, storage and operational cost. Increasing partitions is not a universal scaling fix; it can change key-to-partition mapping and ordering assumptions.

### Producer acknowledgements and durability

Producer `acks`, replication factor, minimum in-sync replicas and broker failure policy jointly influence durability and availability. Understand the configured combination rather than claiming “Kafka is durable” without qualification.

Idempotent producer behavior can suppress duplicates caused by producer retries within its supported session/sequence semantics. It does not automatically make an arbitrary database update or external API call exactly once.

### Consumer groups and offsets

A consumer group shares partition assignments among its members. Rebalances occur when membership or partition topology changes. Processing latency, heartbeat/session configuration and long blocking work can affect group stability.

Offset commit strategies:
- Commit before processing: at-most-once risk (failure may lose work).
- Commit after processing: at-least-once risk (failure after side effect before commit can replay).
- Transactional Kafka consume-produce can atomically commit output records and offsets within Kafka's transaction boundary, but external DB effects still need their own coordination/idempotency.

### Rebalancing and lag

Monitor consumer lag and, more usefully, oldest record age. Lag can be expected during bursts; alert on sustained growth and time-to-drain relative to SLA. Rebalance churn can reduce throughput and cause duplicate processing under at-least-once designs.

## 3. Kafka key and ordering design

Use a key that reflects the required ordering boundary, commonly aggregate ID such as orderId. Same key maps to a partition under the partitioner, preserving partition order.

Trade-offs:
- Too few partitions limits concurrency.
- Random keys lose per-entity ordering.
- Hot keys create skew and a single-partition bottleneck.
- Partition expansion can remap keys and affect ordering across old/new partition placement.

If strict order is required for one hot entity, accept its serialized throughput or redesign the invariant.

## 4. RabbitMQ architecture

### Exchanges and routing

RabbitMQ exchanges route messages to queues using binding rules:
- **Direct:** exact routing-key match.
- **Topic:** pattern-based routing.
- **Fanout:** broadcast to bound queues.
- **Headers:** route based on headers.

Queues hold messages for consumers. Durable queues, persistent messages and publisher confirms are separate parts of a reliability configuration; configure all required layers.

### Acknowledgements and redelivery

Manual acknowledgements let the consumer acknowledge only after processing. If a consumer dies before ack, the message can be redelivered. This is at-least-once behavior and requires idempotent side effects.

Acknowledge after durable business completion. Acknowledge-before-write risks loss; ack-after-write risks duplicate replay.

### Prefetch and competing consumers

Prefetch limits unacknowledged deliveries per consumer/channel and can provide backpressure. Too high can cause uneven distribution and memory pressure; too low may reduce throughput. Tune based on processing time, concurrency and message size.

Competing consumers distribute work across queue consumers. Ordering can be affected by multiple consumers, retries and redelivery; don't promise strict global ordering without a deliberate design.

## 5. Retry, poison messages and DLQ

A retry policy should distinguish:
- Transient infrastructure failure.
- Rate limiting.
- Invalid schema or business validation.
- Permanent domain rejection.
- Poison message that repeatedly crashes processing.

Use bounded retry attempts and backoff. Avoid immediate requeue loops that consume broker CPU and starve healthy traffic. Route exhausted messages to a DLQ with original metadata, failure reason, attempt count and trace context.

DLQ is not a trash bin: define ownership, alerting, replay tooling, payload redaction and safe reprocessing. Fix the underlying issue before replaying a large backlog.

## 6. Schema evolution

Events are long-lived contracts. Use schema registry or disciplined versioning with compatibility checks.

Safe evolution commonly includes adding optional fields with defaults. Renaming/removing fields or changing semantic meaning is breaking even if serialization still parses.

- Version schema explicitly.
- Preserve event identity and business semantics.
- Test old producer/new consumer and new producer/old consumer combinations.
- Use tolerant readers without silently accepting invalid required data.
- Never reuse a field for a different meaning.

## 7. Kafka vs RabbitMQ decision matrix

| Requirement | Kafka tendency | RabbitMQ tendency |
|---|---|---|
| Durable event history and replay | Strong fit | Possible but not primary log model |
| High-throughput partitioned stream | Strong fit | Depends on queue topology/workload |
| Flexible exchange/routing | Less broker-native; topics/consumer logic | Strong fit |
| Consumer-controlled replay by offset | Native | Queue lifecycle/redelivery differs |
| Work queue with per-message ack | Possible, but model differs | Natural fit |
| Per-key ordering at scale | Partition key | Queue/single-consumer or sharded pattern |
| Operational concern | Partition, replication, lag, retention | Queue depth, ack, prefetch, routing and memory/disk alarms |

A hybrid architecture can be valid, but introduces duplicated operational skills, connectors and failure modes. Do not use two brokers merely to satisfy technology checklists.

## 8. Reliability patterns with a database

### Outbox publisher

Write domain state and outbox event in one DB transaction. Publish asynchronously; mark or checkpoint only after broker confirmation. Crash windows can cause duplicate publish, so event ID and consumer idempotency are mandatory.

### Consumer transaction

For a DB-backed consumer:
1. Begin local DB transaction.
2. Insert event ID into inbox/dedup table under unique constraint.
3. If already processed, skip business effect.
4. Apply state change.
5. Commit DB.
6. Acknowledge broker message.

This protects against redelivery after a successful commit but before acknowledgement.

### External side effects

If consumer calls an external API, use the API's idempotency key or a durable workflow. A DB inbox alone cannot atomically commit an external payment.

## 9. Operations and performance

Monitor:
- Producer send latency, error rate, retries and buffer saturation.
- Broker disk/replication health, under-replicated partitions and ISR changes.
- Consumer lag/oldest age, processing duration and rebalance count.
- RabbitMQ ready/unacked count, queue age, redelivery, connection/channel churn.
- DLQ count/age and retry rate.
- Payload size, serialization cost and compression ratio.
- Resource saturation: CPU, disk, network, memory and file descriptors.

Capacity estimate should include average and peak event rate, bytes/event, retention, replication, consumer processing time, burst backlog and recovery throughput.

## 10. Production scenarios

### Consumer commits offset before DB commit
Failure loses event effect. Change commit order and use idempotent processing.

### DB commit succeeds, broker ack fails
Message redelivers. Inbox dedup ensures the business transaction is not applied twice.

### Kafka lag rises after deployment
Inspect processing latency, downstream DB, rebalance, partition skew and consumer concurrency; scale only if partitions and downstream capacity allow.

### Rabbit queue grows continuously
Producer rate exceeds consumer rate. Check processing duration, prefetch, worker count, poison-message loop and downstream saturation. Apply backpressure or scale after finding bottleneck.

### DLQ replay creates duplicates
Replay with original event IDs, idempotency and controlled rate. Record replay batch and outcome.

## 11. Exercises

1. Select a Kafka partition key for order events and explain hot-key risk.
2. Design RabbitMQ exchange/queue bindings for notifications by channel and tenant.
3. Implement an idempotent consumer with inbox uniqueness and transactional business update.
4. Configure bounded retry and DLQ routing for transient vs permanent failures.
5. Calculate retention storage and consumer catch-up time from throughput assumptions.
6. Explain safe event schema evolution across rolling producer/consumer deployments.

## 12. Senior / architect interview questions

- What does Kafka guarantee about ordering and where?
- How do partitions affect consumer-group parallelism?
- Compare producer idempotence with end-to-end exactly-once.
- What causes a consumer rebalance and how do you reduce churn?
- Why is offset commit order important?
- Compare RabbitMQ exchanges and Kafka topics.
- How do publisher confirms differ from consumer acknowledgements?
- What is prefetch and how does it affect fairness/backpressure?
- Design retry/DLQ without creating poison-message loops.
- How do you make a DB-backed consumer idempotent?
- How do you evolve event schemas safely?
- What operational metrics indicate a broker is healthy but the workflow is not?

## References

- Apache Kafka documentation: https://kafka.apache.org/documentation/
- Kafka design: https://kafka.apache.org/documentation/#design
- RabbitMQ tutorials and guides: https://www.rabbitmq.com/tutorials
- RabbitMQ acknowledgements and confirms: https://www.rabbitmq.com/docs/confirms
- CloudEvents specification: https://cloudevents.io/

**Editorial gate:** Verify broker version, client semantics, transaction guarantees and configuration examples against the intended production topology before publication.