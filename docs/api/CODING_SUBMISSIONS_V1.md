# Coding submissions API v1 — issue #197
This slice delivers a durable authenticated control-plane queue; it does not execute learner source.
- POST /api/v1/coding-submissions: authenticated, JAVA only, source <=64 KiB, UUID exercise and idempotency key, 202 receipt.
- GET /api/v1/coding-submissions/{id}: ownership + academy scoped status; unknown/foreign ID is 404.
- POST /api/v1/coding-submissions/{id}/cancel: queued jobs become CANCELLED; running jobs receive cancel_requested.
- Idempotency uniqueness is enforced per learner + academy; source digest stored; execution_mode is DISABLED.
- A separately reviewed isolated worker must lease queue rows and write terminal results. No app process may compile, execute, shell out, or fetch dependencies from learner code.
Deferred: published exercise lookup, quota controls, worker leases/results, retention, real sandbox and Laravel parity. See docs/security/CODING_PRACTICE_SANDBOX_MVP.md.
