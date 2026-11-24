# Admin content operations (5% slice)
- Adds a review-gated, bounded bulk publish surface with per-item version tokens.
- Operations execute sequentially to avoid client-side concurrency races; partial success is surfaced rather than misreported as atomic.
- Only IN_REVIEW content is selectable. The UI caps at 100 records and preserves failed rows for retry.
- Backend is the authority: validate role, review transition, optimistic version, audit record, idempotency key and publication side effects. Never rely on the client status filter for authorization.
- Next integration: attach to content inventory, connect server endpoint and add Playwright coverage against a mocked stale-version response.
