# Spring Boot API contract coverage and backend parity

**Normative contract:** `docs/api/openapi.yaml`  
**Contract version:** 1.5.0  
**Baseline reviewed:** 20 November 2026  
**Primary production backend:** Spring Boot  
**Deployment rule:** one active backend implementation writes to the canonical database at a time.

## Contract completion status

The OpenAPI route inventory is now complete for the **implemented** Spring Boot and Laravel routes. CI runs the Spring controller/OpenAPI and Laravel route/OpenAPI coverage check, OpenAPI lint, and unique operation-ID validation. Shared `error-response-parity.json` fixtures are consumed by both backends; HTTP tests cover successful JSON responses, validation errors, authentication failures, permission denial, not-found and sanitized server errors.

This is contract completeness for the implemented surface, not a claim that both backends have identical feature sets. The active production implementation is Spring Boot. Laravel remains a secondary compatibility backend, and the feature/authorization/session differences below are explicitly deferred. Mobile and admin clients must use the normative Spring contract and must not branch on an inactive backend.

The remaining items below are **feature parity work**, not undocumented routes or a reason to dual-write. Close parity gaps only with matching route behavior, permission tests and release evidence.

## Spring Boot route coverage

Every Spring MVC controller route under `backend/src/main/java` must be represented by the OpenAPI document. The `verify-spring-openapi-routes.mjs` CI check extracts class-level and method-level Spring mapping annotations and fails if a route is undocumented. This is a route-presence gate; it does not replace request/response schema, authorization or integration tests.

Spring-first capabilities currently include content authoring, review, publishing, lesson archiving, taxonomy management, learner progress/bookmarks, content release manifests and admin question operations. These routes are part of the normative Spring contract even where Laravel has no equivalent handler.

## Laravel compatibility boundary

Laravel remains a secondary compatibility implementation. The existing shared authentication, catalogue and quiz endpoints are the current parity baseline. Spring-first CMS and learning-continuity endpoints are not required to ship in Laravel and must not be described as implemented there until matching routes, authorization, persistence behavior and tests exist.

Do not add undocumented Laravel-only behavior required by Android or Admin clients. If a backend switch is planned, complete the parity inventory and contract tests before switching traffic; never dual-write to both implementations.

## Known parity work

1. **Authentication throttling:** Laravel route-level throttling and Spring Boot controls are not yet identical for every public auth route. Phone OTP has narrower service-level limits. Keep these differences explicit until equivalent controls and tests exist.
2. **Quiz validation:** Laravel validates nested answer fields and non-negative `timeSpentMs`; Spring currently parses generic maps and validates key constraints. Align nested validation before claiming full parity.
3. **Error envelopes:** Both backends use the shared `code` and `message` fields with optional field-level `errors`; continue covering framework-generated failures.
4. **Permission enforcement:** Spring method security and Laravel permission middleware exist, but persisted role-to-permission behavior needs end-to-end parity coverage before treating authorization as equivalent.
5. **Session lifecycle:** Spring refresh-token rotation and password recovery are Spring-first features until Laravel and all clients support the same contract. Do not imply Laravel parity.
6. **Content lifecycle:** Spring content authoring/review/publishing and academy isolation are authoritative. Laravel parity is intentionally deferred and is not a release blocker for the Spring-first product increment.

## CI contract gates

- Lint `docs/api/openapi.yaml` on pull requests and pushes to `main`.
- Fail CI if any Spring controller route is missing from OpenAPI.
- Keep request/response schemas, status codes, validation and authorization documented for each implemented route.
- Add PostgreSQL integration tests for lifecycle transitions, ownership, academy isolation and learner activity.
- Add shared behavioral contract tests before declaring Spring/Laravel parity or switching the active backend.

## Definition of done

- Every implemented Spring Boot route is present in OpenAPI.
- Every published route has accurate request/response schemas and status/error semantics.
- Public versus authenticated access and named permissions are explicit.
- Spring integration tests cover success, validation, authorization, ownership and not-found behavior.
- Laravel parity is claimed only for routes with matching handlers and passing tests.
- Mobile and Admin clients do not need backend-specific branching for the active Spring API.
