# Learner Progress Event Contract (v1)

Issue #189 contract/schema slice. Mastery remains server-derived and is never accepted from clients.

## Event envelope
The currently implemented endpoint accepts one event per request: client-generated UUID `eventId` stable across retries; `eventType` (LESSON_STARTED, LESSON_COMPLETED, QUESTION_ANSWERED, QUIZ_SUBMITTED, BOOKMARK_CHANGED, REVIEW_COMPLETED); RFC 3339 `occurredAt`; optional published `lessonSlug`; and an optional bounded object `payload`. The server fixes schema version 1 and derives authenticated `userId` and academy membership; clients do not submit these identity/scope fields.

## Idempotency and ordering
- Unique key: authenticated user + academy + event ID.
- Identical duplicate returns DUPLICATE and causes no second side effect.
- Reuse of an ID with a different event type, timestamp, lesson association or payload is rejected with HTTP 409.
- Events are append-only; projection updates occur in the same transaction as insertion.
- Client timestamps are descriptive, not authoritative. Retain server receipt time and clamp implausible timestamps for projection.
- Completion is monotonic; stale progress cannot regress completed state or newer content revision.
- Bookmark ordering must use server sequence/version, not client wall-clock alone.

## Request and retry behavior
Both single-event and batch ingestion are implemented. `POST /me/progress-events` returns one receipt; `POST /me/progress-events/batch` accepts 1–100 events and returns `events`, `acceptedCount`, and `duplicateCount`. Each receipt contains the original `eventId`, `ACCEPTED` or `DUPLICATE`, and `occurredAt`. The batch is processed in one Spring transaction: an invalid event or conflicting reuse of an event ID fails the request rather than returning a partial per-item rejection list. Identical retries retain their original IDs and bodies. Validation and authorization failures are permanent; network, 408, 429 and 5xx are retryable with exponential backoff and jitter.

## Privacy and tenant isolation
Scope all access by authenticated user and academy. Never accept client mastery, answer keys, credentials, raw device identifiers, or unnecessary personal data. The service rejects payloads above 8 KiB; the database also applies a 16 KiB storage constraint.

## Delivery boundary
The ledger and single-event Spring Boot ingestion service are implemented. Batch ingestion and paginated event history are implemented. Remaining work includes durable Android outbox integration with the batch receipt contract, transactional derived projections where required, end-to-end reconciliation/conflict recovery, integration/concurrency coverage and maintained Laravel compatibility. The mobile worker still uses legacy progress/bookmark endpoints; do not claim offline event reconciliation is complete.
