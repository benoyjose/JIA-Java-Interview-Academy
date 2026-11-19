# Multi-Academy Platform Strategy — Separate Apps, Shared Backend and Admin

## Product family and distribution
- JIA — Java Interview Academy: first production app.
- PIA — Python Interview Academy: future separately marketed app.
- GIA — Go Interview Academy: future separately marketed app.

Each academy has its own Android and iOS store listing, brand identity, app name, icon, screenshots, package/bundle identifier, store metadata and release lifecycle. They are distinct customer-facing apps, not one app with an academy selector.

The apps share a codebase and platform modules where practical. All apps connect to the same backend platform, canonical database, storage, authentication, content delivery, assessment engine, subscription/entitlement services and central admin portal.

## Shared platform capabilities
Identity/authentication; profiles; academy catalogue/enrollment; roles/permissions; content authoring/review/publishing/versioning; learning paths/prerequisites; assessments/question bank; attempts/scoring/feedback; progress/bookmarks/revision scheduling; search/recommendations; notifications; subscriptions/payments/entitlements; analytics; audit; feature flags; content manifests/offline sync; app registry and release management.

## Academy-specific configuration
Academy code/name/branding/theme; curriculum taxonomy and skills; lessons/examples/question bank; supported language/runtime/toolchain versions; role tracks; learning-path rules and search vocabulary; store metadata and app release configuration.

## Separate application identities
Maintain an academy_apps registry. Each record maps one registered app identity to one academy and platform:
- app_code (e.g. JIA_ANDROID, JIA_IOS), academy_id, platform (ANDROID/IOS), application_id (Android package name or iOS bundle identifier), display_name, status, minimum_supported_version, current_version, store_url and timestamps.
- Enforce unique (platform, application_id) and unique app_code.
- Register JIA apps initially; add PIA/GIA records only when their apps are prepared for release.
- The public app code identifies academy context; it is not a secret and must not be treated as proof of authenticity.
- API resolves app code against this registry, validates platform/status and scopes requests to the mapped academy. Authentication and user entitlement checks remain separate.
- Never trust a client-supplied academy ID without server-side mapping and authorization.
- App signing, store distribution, platform attestation where justified, rate limits and normal API security provide additional controls; package/bundle identifiers alone are not cryptographic proof.

## Domain model direction
- academies: id, code (JIA/PIA/GIA), name, status, branding/config, timestamps.
- academy_apps: app_code, academy_id, platform, application_id, display_name, status, minimum/current version and store URL.
- academy_versions: academy_id, language/runtime version, support status, release notes and effective dates.
- tracks, categories and lessons, all academy-scoped.
- skills, lesson_skills, prerequisites, learning_paths and learning_path_items.
- questions, options, test cases, rubrics and tags.
- assessment_templates, assessment_items, attempts and responses.
- user_academy_enrollments, user_progress, bookmarks and review_schedule.
- content_releases, release_items and sync_receipts.
- plans, subscriptions, entitlements and provider-abstracted payments.
- audit_events, admin_actions and feature_flags.

Current JIA uses technologies as its top-level catalogue entity. Preserve that schema/API for now; introduce an adapter or controlled migration to generalized tracks. Do not rename or drop current tables without a migration plan.

## Isolation and authorization
A user has one global identity and independent enrollment, entitlement, progress and attempts per academy.
- Every academy-owned record carries academy_id directly or inherits it through a parent with enforced foreign keys.
- Resolve academy from registered app context, not an arbitrary client academy ID.
- Validate user enrollment and entitlement for every protected operation.
- Use composite unique constraints and foreign keys to prevent cross-academy references.
- Enforce academy filters in repositories/services and contract tests.
- Editor/reviewer assignments are academy-scoped; global platform admins are explicit.
- Only one backend implementation is active in production at a time. Spring Boot and Laravel remain equivalent; do not dual-write.

## Shared admin portal
One central React/TypeScript admin portal manages JIA, PIA and GIA:
- Global dashboard and academy switcher.
- Academy-specific curriculum, lesson and question-bank management.
- Academy-scoped editor/reviewer roles and review queues.
- Independent publishing schedules and content releases per academy.
- Separate Android/iOS app metadata, version policy, store links and release notes.
- Academy-level subscriptions, learner analytics and reporting, with consolidated cross-academy reporting for authorized global admins.
- Audit trail records academy context for every action.

No content operation may accidentally publish to every academy. Bulk actions require explicit academy scope and confirmation.

## API direction
- Resolve registered app using a public app code header such as X-Academy-App: JIA_ANDROID; the server maps it to the academy. This is a routing/context hint, not an auth secret.
- Public academy metadata may be exposed through academy catalogue endpoints.
- Academy-scoped APIs use resolved app context, or explicit academy code that must match registered app context and authenticated user's entitlement.
- Keep existing JIA /technologies endpoints as compatibility routes until JIA clients migrate.

## Client applications and release management
Use shared Android modules with separate product flavors/application IDs and shared iOS modules with separate app targets/bundle IDs. Each app receives its own branding, app icon, launch screen, store metadata, signing configuration, privacy disclosures and release pipeline.

Lesson/catalogue changes are delivered through content manifests and do not require app-store releases. Binary releases remain independent per app/platform and follow Android/iOS platform policies.

## Backend architecture
Start with a modular monolith: identity, academy-registry, academy-catalogue, content-authoring, publishing, assessment, learning-progress, search, subscriptions/entitlements, notifications, analytics, audit, app-registry and release-sync.

## Migration sequence
1. Keep JIA as the only active academy and register JIA Android/iOS app identities when package/bundle IDs are finalized.
2. Add academy scope to catalogue while preserving JIA API responses.
3. Implement shared admin portal with academy context and scoped authorization.
4. Implement authoring, review, publishing and question bank.
5. Implement assessments, progress, subscriptions and sync.
6. Launch JIA Android/iOS as separate branded apps against shared backend/admin.
7. Add PIA as a new academy, two app registrations, separate store listings and curated content.
8. Add GIA similarly.
9. Add separate infrastructure only if measurable scale, legal or availability needs justify it.

JIA launch remains the priority. Do not register empty PIA/GIA public apps or invent their lesson content.
