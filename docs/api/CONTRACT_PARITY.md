# API contract inventory and parity baseline

**Contract:** `docs/api/openapi.yaml`  
**Contract version:** 1.3.0  
**Baseline reviewed:** 19 November 2026  
**Implementations:** Spring Boot and Laravel  
**Deployment rule:** one active backend implementation writes to the canonical database at a time.

## Scope of this baseline

This inventory covers routes that exist in both backend implementations today. CMS authoring/review/publishing, learner progress, bookmarks, content manifests/synchronization, subscription entitlements and app-release APIs are product requirements, but are not represented as implemented endpoints until both backends have working handlers, authorization and tests.

## Route parity inventory

| Method | Path | Access | Spring Boot | Laravel |
|---|---|---|---|---|
| POST | `/auth/register` | Public; equivalent throttling not implemented in both | AuthController | AuthController |
| POST | `/auth/login` | Public; equivalent throttling not implemented in both | AuthController | AuthController |
| POST | `/auth/federated/google` | Public; equivalent throttling not implemented in both | FederatedAuthController | FederatedAuthController |
| POST | `/auth/federated/apple` | Public; equivalent throttling not implemented in both | FederatedAuthController | FederatedAuthController |
| POST | `/auth/phone/otp` | Public; rate-limit behavior differs | PhoneOtpController | PhoneOtpController |
| POST | `/auth/phone/verify` | Public; equivalent throttling not implemented in both | PhoneOtpController | PhoneOtpController |
| GET | `/auth/me` | Authenticated | AuthController | AuthController |
| POST | `/auth/logout` | Authenticated | AuthController | AuthController |
| GET | `/technologies` | Public | CatalogueController | CatalogueController |
| GET | `/technologies/{slug}` | Public | CatalogueController | CatalogueController |
| GET | `/technologies/{technologySlug}/categories` | Public | CatalogueController | CatalogueController |
| GET | `/categories/{categorySlug}/lessons` | Public | CatalogueController | CatalogueController |
| GET | `/lessons/{slug}` | Public | CatalogueController | CatalogueController |
| GET | `/quizzes/availability` | Public | QuizController | QuizController |
| POST | `/quiz-attempts` | Authenticated | QuizController | QuizController |
| POST | `/quiz-attempts/{attemptId}/submit` | Authenticated | QuizController | QuizController |
| GET | `/me/quiz-performance` | Authenticated | QuizController | QuizController |
| GET | `/me/quiz-attempts` | Authenticated | QuizController | QuizController |

All paths above are relative to `/api/v1`.

## Contract findings and known parity work

1. **Rate-limit parity is incomplete.** Laravel applies route-level throttling to public authentication routes. Spring security currently permits these routes without equivalent route-level throttles; the phone OTP service has a narrower per-phone request limit. Do not describe the six routes as uniformly throttled until equivalent limits and tests exist in both backends.
2. **Error envelope normalization is implemented for covered paths.** Both backends now emit the shared `code` and `message` fields, with optional field-level `errors`. Continue testing framework-generated edge cases before declaring complete parity.
3. **Quiz request validation differs.** Laravel validates nested answer fields and non-negative `timeSpentMs`. Spring currently parses generic maps and validates answer positions, but does not apply equivalent constraints to every nested field. Tighten Spring validation or extract shared validation rules, then add matching tests.
4. **Authentication failure envelopes are normalized for covered paths.** Spring Security and Laravel middleware have common error handlers. Android accepts the common `message` field and retains `detail` compatibility for older responses.
5. **Public and authenticated route policies must stay explicit.** Catalogue and availability are public. Personal quiz attempts/history/performance and session endpoints require bearer authentication.
6. **Phone number validation is E.164.** The OpenAPI patterns for both OTP request and verification must represent a literal leading plus and match the backend constraints.
7. **Nullable performance fields are valid.** Topic performance may contain a null technology slug for an unfiltered attempt; schemas must not incorrectly require a non-null value.
8. **No refresh-token API exists yet.** The current token response is a 30-day opaque bearer token. Do not document refresh-token behavior until both implementations and clients support it.
9. **Permission enforcement is not yet mapped to a shared production route.** Spring method security is enabled and tested with a protected test endpoint; Laravel has reusable `RequireJiaPermission` middleware. No current shared production route applies a matching named permission policy, and persisted-token permission loading still needs end-to-end parity coverage. Do not treat this as complete authorization parity.
10. **Expired persisted bearer tokens are covered by integration tests.** Both implementations query the `api_tokens` table with `expires_at > now`; tests seed an already-expired token and verify `/auth/me` rejects it with the common 401 envelope without updating `last_used_at`.
11. **No CMS/progress/sync routes exist yet.** Add those paths to this contract in the same change that implements both backend handlers, permission checks and tests.

## Required CI contract gates

- Lint the OpenAPI document on pull requests and pushes to the default branch.
- Keep the route inventory synchronized with both backend route declarations.
- Add contract tests against each backend for status codes, response schemas, validation, authentication, named permissions and ownership.
- Add a parity test for representative requests so the same fixtures produce equivalent status codes and JSON semantics.
- Fail CI when an implementation adds/removes a route without updating the contract and inventory.

## Definition of done for API parity

- Every implemented API route is documented.
- Every documented route is implemented in both backends, or explicitly marked as a planned route outside the published contract.
- Success payloads and error semantics are equivalent.
- Validation and authorization behavior is equivalent, including named permissions derived from persisted roles.
- Both implementations pass the same contract test suite.
- The mobile clients require no backend-specific branching.
