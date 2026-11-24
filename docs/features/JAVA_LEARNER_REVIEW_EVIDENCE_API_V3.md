# Java learner review evidence API
GET /api/v1/me/reviews returns a bounded due queue assembled from submitted quiz evidence and the adaptive scheduler.
- Identity comes from Spring Security CurrentUser; no userId query parameter is accepted.
- SQL scopes attempts by authenticated user and academy, and aggregates answer accuracy and last submission.
- Limit is strictly 1–100; scheduler preserves deterministic priority/rationale.
- Integration contract: verify schema migration names, ensure submitted_at non-null, add integration tests with two users and ensure no cross-user leakage, and apply API rate limiting.
