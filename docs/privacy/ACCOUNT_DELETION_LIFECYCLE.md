# Learner account deletion lifecycle (issue #195)

## Current implementation boundary

The authenticated portability endpoint `GET /api/v1/me/privacy/export` exists. This document specifies the missing deletion lifecycle; it does not claim account deletion is implemented.

## Required API contract

Proposed endpoint: `DELETE /api/v1/me/privacy/account` (or a confirmation resource followed by DELETE; select one canonical shape in the OpenAPI review before implementation).

- Require a valid authenticated session and recent re-authentication (password, federated re-auth, or a short-lived server-issued confirmation challenge).
- Require explicit confirmation; never delete from a GET or a background notification action.
- Resolve user identity exclusively from the authenticated principal. Ignore any client-supplied user/account ID.
- Return an asynchronous deletion receipt if provider/retention work cannot complete transactionally; receipt must expose only request ID, state and timestamps.
- Idempotent repeated confirmation must return the existing request state rather than duplicate destructive work.

## State machine

`ACTIVE -> DELETION_REQUESTED -> DELETION_IN_PROGRESS -> DELETED`

A reversible grace period is permitted only if policy is explicit and cancellation is authenticated. Once irreversible processing starts, cancellation must be rejected.

## Data handling checklist

1. Revoke refresh/access sessions and invalidate active device sessions immediately after accepted request.
2. Remove or anonymize learner-owned progress, bookmarks, quiz attempts/answers, revision plans, preferences, notification history and queued delivery jobs according to the approved retention schedule.
3. Remove personal identifiers from profile and provider-link records; revoke provider tokens before deleting local references.
4. Delete or rotate user-owned push-token associations; do not retain raw device identifiers in the deletion receipt.
5. Keep only records legally required for billing, fraud, security or audit, with documented purpose, minimum fields and expiry. Do not silently retain learning history.
6. Ensure deletion worker retries are idempotent, observable without exposing PII, and isolated by authenticated user ID.
7. Define treatment of authored/admin-owned content separately from learner account deletion.

## Security and acceptance tests

- Unauthenticated request: 401; non-owner cannot request another account's deletion.
- Missing/expired confirmation or failed recent re-authentication: reject without mutating data.
- Repeated request: one deletion job and stable receipt.
- Session tokens are invalid immediately after acceptance.
- Failure midway: retry resumes safely; no cross-user deletion.
- Verify each learner-owned table, provider link, push token and queued notification is removed or retained only under an explicit policy.
- Export is available before deletion, but is no longer available after completion.
- API schema, Spring integration tests, Laravel parity decision, and privacy policy must agree before release.

## Delivery sequencing

Implement contract and threat model first; then transactional request/receipt persistence, revocation, asynchronous deletion worker, retention exceptions, API tests, and operational runbook. Track this as a separate implementation PR from the existing export endpoint.
