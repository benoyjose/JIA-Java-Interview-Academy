# M2 — Offline progress reconciliation and recovery (#189, #244)

## Durable client queue
Persist each event before attempting network delivery. Minimum states:
- queued
- sending (lease/worker ownership, not a permanent state)
- accepted
- duplicate
- retryable_failure
- permanent_rejection
- needs_user_action

Only remove or compact an event after a server receipt confirms accepted/duplicate, or after the learner explicitly resets local data. App termination must recover expired sending leases to queued.

## Batch protocol
- Client supplies event IDs and schema version; server derives authenticated learner and academy scope.
- Server response includes a receipt for every submitted ID, with status, stable error code, retryability and optional remediation.
- Entire batch is transactionally accepted or the contract explicitly reports per-event atomicity. Never return ambiguous success.
- Same event ID + same canonical payload is duplicate success. Same ID + different payload is conflict (409); do not overwrite original evidence.
- Authorization, academy mismatch, unsupported schema and invalid event are permanent until user/session/content action; network/5xx/429 are retryable with bounded exponential backoff and jitter.
- Expired auth pauses sync, preserves queue, and resumes only after secure re-authentication.

## Worker and resource rules
- At most one active sync worker per local account/academy queue.
- Use bounded batches, connectivity-aware scheduling and retry caps; expose last sync, pending count and failure category without exposing answer content.
- Queue storage limits must be explicit. At capacity, preserve existing events and warn the learner; never silently drop.
- Changing account or academy partitions queues. Sign-out must not upload one user's events under another identity.

## Conflict/reconciliation rules
- Event identity is immutable; server receipt is durable and idempotent.
- Projection is derived from accepted ledger events, not client aggregate snapshots.
- Clock skew is tolerated with server receipt ordering and documented domain tie-breakers; client timestamp alone cannot grant mastery.
- Partial response handling retries only unresolved/retryable IDs.
- Content revision mismatch retains the event and returns a resolvable stale-content outcome.

## Required automated tests
- Process death in queued/sending; worker lease recovery; offline-to-online.
- Duplicate concurrent requests and same-ID/different-body conflict.
- Partial receipts, server rollback, 429/5xx and bounded retry.
- Expired token, sign-out/account switch, academy switch, schema rejection.
- Queue cap, storage migration and explicit reset.
- Projection determinism for duplicate/out-of-order batches.

## Completion boundary
#189 remains open for canonical event ledger, batch ingestion, projections and API tests. #244 remains open until Android/iOS queue implementations demonstrate no data loss under process death and recovery. Keep this protocol platform-neutral and align with #226 parity checks.
