# Learner Progress Event Contract (v1)

Issue #189 contract/schema slice. Mastery remains server-derived and is never accepted from clients.

## Event envelope
Each event includes a client-generated UUID `eventId` stable across retries; `eventType` (LESSON_STARTED, LESSON_COMPLETED, QUESTION_ANSWERED, QUIZ_SUBMITTED, BOOKMARK_CHANGED, REVIEW_COMPLETED); `schemaVersion` 1; validated `academyCode`; RFC 3339 `occurredAt`; optional lesson UUID; and bounded event-specific metadata. Authenticated identity supplies `userId`.

## Idempotency and ordering
- Unique key: authenticated user + academy + event ID.
- Identical duplicate returns DUPLICATE and causes no second side effect.
- Reuse of an ID with a different normalized body is rejected as EVENT_ID_REUSE.
- Events are append-only; projection updates occur in the same transaction as insertion.
- Client timestamps are descriptive, not authoritative. Retain server receipt time and clamp implausible timestamps for projection.
- Completion is monotonic; stale progress cannot regress completed state or newer content revision.
- Bookmark ordering must use server sequence/version, not client wall-clock alone.

## Batch API and retry
`POST /me/progress-events` accepts 1–100 events and returns per-item ACCEPTED, DUPLICATE, or REJECTED outcomes. Clients inspect each outcome. Validation/auth failures are permanent; network, 408, 429 and 5xx are retryable with exponential backoff and jitter. Queue states: PENDING → IN_FLIGHT → ACKNOWLEDGED; transient failure returns to PENDING; permanent failure becomes REJECTED. On restart, stale IN_FLIGHT items return to PENDING.

## Privacy and tenant isolation
Scope all access by authenticated user and academy. Never accept client mastery, answer keys, credentials, raw device identifiers, or unnecessary personal data. Payload is limited to 16 KiB by database constraint.

## Delivery boundary
This PR adds the contract, OpenAPI endpoint and ledger schema. Transactional service/projection, integration tests and maintained Laravel compatibility remain follow-up work; issue #189 is not complete until those are implemented.
