# Learner search, recommendations and revision reminders

## Search
Authenticated endpoint: `GET /api/v1/me/academies/{academyCode}/search?q=...`. Search is bounded to 2–100 characters and 50 results. Initial ranking is deterministic by content kind (technology, category, lesson, question) and title. It searches published curriculum and the current question revision only; draft, in-review and archived questions are excluded. Results are scoped by active academy membership. Premium lessons are omitted unless the learner has the required, unexpired academy entitlement. Search is intentionally database-backed; no external search index or unbounded wildcard query is required for this initial version.

## Recommendations
`GET /api/v1/me/academies/{academyCode}/recommendations` ranks published, uncompleted lessons using explainable signals:
1. Bookmarked lesson
2. In-progress lesson (continue learning)
3. Lessons in a technology track where the learner has completed content
4. General published starting points

Tie-breaking uses category order, title and stable lesson ID. The algorithm is deterministic and uses progress/bookmarks, not opaque ML. Academy membership, publication state and premium entitlement checks are applied to candidates.

## Revision schedule and notification controls
Completing a lesson seeds a learner-specific review schedule. The review action advances bounded intervals (1, 3, 7, 14, 30 and 60 days). The revision schedule endpoint materializes due in-app reminders with a unique user/academy/dedupe key, so repeated polling or retry cannot create duplicate reminders.

Learners can disable reminders and configure timezone and quiet hours (including intervals that cross midnight). Reminder rows are academy scoped and the inbox returns only the authenticated learner's due notifications. A learner can mark their own notification read; ownership is checked in the update predicate.

## Delivery boundary
This increment implements in-app notification persistence and preference policy. External push delivery is deliberately behind a provider adapter and deployment configuration; setting a push preference does not bypass provider configuration or send a push by itself. Do not store provider credentials in source control. The database queue is the durable integration point for a bounded retry worker with delivery idempotency.
