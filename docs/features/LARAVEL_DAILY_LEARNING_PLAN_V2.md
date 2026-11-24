# Laravel daily learning plan (5% implementation slice)
- Compose an explainable session from learner-scoped topics, due review IDs and the configured daily commitment.
- Enforce 5–480 minute availability and 5–240 minute goal bounds; never silently create an unbounded session.
- Deduplicate review references, allocate a small review-first budget, rank topics by weakness, and emit a reason per activity.
- Domain boundary: caller must obtain topic/evidence data through the authenticated learner's repositories. This composer intentionally does not accept learner IDs or query another learner.
- Follow-up integration: authenticated endpoint, persistence of generated plan, progress events and timezone-aware daily streak transaction.
- Validation: PHPUnit unit coverage for ordering, deduplication, budget limits and invalid settings.
