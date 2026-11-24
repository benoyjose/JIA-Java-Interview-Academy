# Learner commitment and streak API
- Migration persists per-learner local-date goal, completed minutes, timezone and completed-session count with a unique learner/date key.
- Authenticated GET/POST endpoints derive identity only from the existing JIA token middleware.
- Timezone is validated and the current local date is calculated with Carbon; goals and duration are bounded.
- Writes are transactional and lock the learner/date row to avoid lost updates.
- Follow-up hardening: add idempotency-key storage for sessionId, privacy retention policy and race-proof insert-on-conflict behavior for first-write concurrency. Do not accept userId from clients.
