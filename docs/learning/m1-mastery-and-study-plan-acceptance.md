# M1 — Learner mastery and study plan acceptance (#186)

## Product behavior
- A new learner receives a deterministic, prerequisite-valid starter plan; no activity history is required.
- Plans combine curriculum prerequisites, learner-selected goals, available study time, due reviews, and verified progress.
- Lesson opened/viewed is engagement only; it must not award mastery.
- Lesson completion and assessment evidence are distinct. Mastery is derived server-side from versioned evidence and thresholds.
- Recommendations explain the reason and action (prerequisite, weak objective, due review, or goal alignment); never expose opaque scores as the only explanation.
- Preserve a learner's ability to browse and choose content outside the recommended plan.

## Canonical learning evidence
Each event has a stable event ID, learner and academy scope resolved by the server, content/revision ID, event type, client occurrence timestamp, server receipt timestamp, schema version, and minimal device/session metadata. Client mastery, streak, or aggregate score is advisory and never authoritative.

## Plan and mastery invariants
1. Same curriculum version + same accepted event set + same algorithm version produces the same mastery/plan result.
2. Duplicate events are idempotent; out-of-order receipt does not reverse stronger evidence.
3. Curriculum or algorithm changes are versioned; historical results remain interpretable.
4. Deleted/unpublished content does not break existing progress references.
5. No-history, partial-history, stale-offline, and unavailable-content states have valid user-facing outcomes.
6. Timezone and local study-day boundaries are explicit; DST changes must not double-count daily goals.

## Minimum learner plan response
Return plan version, generated timestamp, algorithm/curriculum versions, goal target, ordered actionable items, reason codes, prerequisites, estimated duration, due-review date where relevant, and a non-blocking explanation for items unavailable offline.

## Test scenarios
- Fresh learner; learner with only lesson views; learner with quiz attempts; weak objective; overdue review.
- Duplicate and reordered event batches produce identical projection.
- Curriculum prerequisite cycle/unknown prerequisite is rejected during publication.
- Changed curriculum revision does not silently rewrite historic evidence.
- Cross-academy and cross-learner data cannot affect recommendations.
- Day boundary/DST and paused goal handling.
- Plan generation remains deterministic under retries and concurrent projection refresh.

## Delivery checklist
- Domain model and OpenAPI documented.
- Spring Boot projection/service and integration tests are authoritative.
- Android consumes reason-coded plans and presents loading/empty/stale/error states.
- Laravel mirrors shared API behavior only; no second production writer.
- Content completeness and mastery semantics coordinate with curriculum epic #159.
- Do not close #186 until end-to-end API/client and regression criteria pass.
