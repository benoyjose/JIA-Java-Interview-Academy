# Multi-Academy Platform Strategy — JIA First, PIA and GIA Later

## Product family
- JIA — Java Interview Academy: first production launch.
- PIA — Python Interview Academy: future academy.
- GIA — Go Interview Academy: future academy.

Build one configurable academy platform. Do not fork identity, progress, assessments, publishing, admin or mobile applications for each language.

## Shared platform capabilities
Identity and authentication; user profiles; academy catalogue and enrollment; roles and permissions; content authoring/review/publishing/versioning; learning paths and prerequisites; assessments and question bank; attempts/scoring/feedback; progress/bookmarks/revision scheduling; search and recommendations; notifications; subscriptions/payments/entitlements; analytics; audit; feature flags; content manifests/offline sync; app release metadata.

## Academy-specific configuration
Academy code/name/branding/theme; language-specific curriculum taxonomy and skills; lessons/examples/question bank; supported language/runtime/toolchain versions; role tracks; learning path rules and search vocabulary.

## Domain model direction
- academies: id, code (JIA/PIA/GIA), name, status, branding/config, timestamps.
- academy_versions: academy_id, language/runtime version, support status, release notes and effective dates.
- tracks: academy_id, slug, title, description, order, publication status.
- categories: track_id, slug, title, order and publication status.
- lessons: category_id, slug, title, summary, content/document reference, revision, difficulty, source type and publication status.
- skills, lesson_skills, prerequisites, learning_paths and learning_path_items.
- questions, options, test cases, rubrics and tags.
- assessment_templates, assessment_items, attempts and responses.
- user_academy_enrollments, user_progress, bookmarks and review_schedule.
- content_releases, release_items and sync_receipts.
- plans, subscriptions, entitlements and provider-abstracted payments.
- audit_events, admin_actions and feature_flags.

Current JIA uses technologies as its top-level catalogue entity. Preserve that schema/API for now; introduce an adapter or controlled migration to generalized tracks. Do not rename or drop current tables without a migration plan.

## Isolation and authorization
An academy is a product boundary, not necessarily a separate customer tenant. A user has one global identity and independent enrollment, entitlement, progress and attempts per academy.
- Every academy-owned record must carry academy_id directly or inherit it through a parent with enforced foreign keys.
- Validate academy context server-side; never trust client-supplied academy_id.
- Use composite unique constraints and foreign keys to prevent cross-academy references.
- Enforce academy filters in repositories/services and contract tests.
- Editor/reviewer assignments should be academy-scoped; global platform admins are explicit.
- Only one backend implementation is active in production at a time. Spring Boot and Laravel remain behaviorally equivalent; do not dual-write.

## API direction
New multi-academy endpoints:
- GET /api/v1/academies
- GET /api/v1/academies/{academyCode}
- GET /api/v1/academies/{academyCode}/tracks
- GET /api/v1/academies/{academyCode}/tracks/{trackSlug}/categories
- GET /api/v1/academies/{academyCode}/categories/{categorySlug}/lessons
- GET /api/v1/academies/{academyCode}/lessons/{lessonSlug}

Keep existing /technologies JIA endpoints as compatibility routes until clients migrate. Document response changes and version them; never silently break the mobile contract.

## Client applications
A single Android/iOS app may host multiple academies with academy selection, or later produce separately branded store apps from shared code. Academy branding and catalogue configuration are remotely delivered. Normal content changes must not require a binary update. The shared admin portal must always show the active academy context.

## Backend architecture
Start with a modular monolith: identity, academy-catalogue, content-authoring, publishing, assessment, learning-progress, search, subscriptions/entitlements, notifications, analytics, audit and release-sync. Extract services only when scale, deployment independence or ownership boundaries justify it.

## Migration sequence
1. Add academy registry and seed JIA as the only active academy.
2. Add academy scope to catalogue while preserving JIA API responses.
3. Implement academy-aware authoring, review and publishing.
4. Implement question bank, assessments and learning progress.
5. Launch JIA end-to-end and measure real usage.
6. Add PIA through configuration, curated curriculum and content.
7. Add GIA similarly.
8. Consider separately branded apps only if acquisition/store strategy justifies them.

JIA launch remains the priority. Reserve PIA/GIA codes but do not expose empty catalogues, fabricate lesson bodies or divert launch work.
