# Spring Boot–Laravel parity matrix

**Production writer:** Spring Boot. Laravel is a compatibility implementation only; it must not run as a second production writer. This matrix is the review checklist for shared capabilities, not a claim that every feature is already equivalent.

## Status meanings

- **Parity-required** — both backends implement the documented shared contract and must pass equivalent contract tests before release.
- **Spring-first** — production capability belongs to Spring Boot; Laravel is intentionally not a release blocker until explicitly promoted.
- **Gap** — a known mismatch or missing evidence; include a linked issue and do not mark complete based on route presence alone.

## Capability inventory

| Capability | Contract / route family | Spring Boot evidence | Laravel evidence | Policy / follow-up |
|---|---|---|---|---|
| Authentication and session | `/auth/*`, `/me` | `backend/src/main/java/com/jia/academy/auth` | `backend-laravel/app/Http/Controllers/Api/V1/AuthController.php`, federated and OTP controllers | Parity-required; #6 |
| Public catalogue | `/technologies`, `/categories`, `/lessons`, public search | `backend/src/main/java/com/jia/academy/catalogue` | `backend-laravel/app/Http/Controllers/Api/V1/CatalogueController.php` | Parity-required for documented routes; #226 |
| Assessment lifecycle | `/quiz-attempts/*`, performance/history | `backend/src/main/java/com/jia/academy/assessment` | `backend-laravel/app/Http/Controllers/Api/V1/QuizController.php` | Parity-required; assess #226 |
| Learner progress and sync | `/me/lesson-progress`, `/me/progress-sync/batch` | `backend/src/main/java/com/jia/academy/learning` | `LearnerLibraryController.php`, `LearnerSyncController.php` | Parity-required; #189/#244 |
| Privacy export | `/me/privacy/export` | `backend/src/main/java/com/jia/academy/privacy` | `LearnerPrivacyController.php` | Parity-required; #195 |
| Study plan and commitment | `/me/study-plan`, `/me/commitment*` | `backend/src/main/java/com/jia/academy/learning` | `StudyPlanController.php`, `LearnerCommitmentController.php` | Parity-required |
| Academy app identity / tenant isolation | Registered app context and academy-owned resources | Spring security/context and academy-scoped services | No parity claim from route checks; tenant policy must be verified independently | Spring-first until Laravel parity tests are added; #221 |
| Editorial authoring, review, release | `/admin/lessons*`, review and publication | `backend/src/main/java/com/jia/academy/content` | Lesson import routes exist; full workflow equivalence not established | Spring-first; #187/#190 |
| Subscription entitlement | Purchase verification and entitlement lifecycle | Contract and implementation evidence to be reviewed | No parity claim | Spring-first; #220 |
| Offline content manifests | Versioned package/download lifecycle | Contract and client reconciliation evidence to be reviewed | No parity claim | Spring-first; #219 |

## Release review rules

1. Route parity scripts prove only that literal routes are represented in OpenAPI. They do **not** prove matching validation, authorization, pagination, ownership, error envelopes, transaction semantics, or persistence behavior.
2. For each parity-required change, PR authors must list both implementations, the OpenAPI operation IDs, and tests for success, invalid input, unauthenticated access, ownership/academy denial, and pagination where applicable.
3. Any intentional difference must be recorded in this matrix with its rationale and issue. Do not silently downgrade a parity-required capability.
4. Shared-schema migrations must be additive/backward-compatible while both codebases remain maintained. Spring Boot remains the sole production writer.
5. Update this matrix when a capability changes status; reviewers should reject unsupported “parity complete” claims.
