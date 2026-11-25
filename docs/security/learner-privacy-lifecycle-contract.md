# M4 — Learner privacy and account lifecycle contract

This document defines the first implementation slice for issue #195. It is a contract, not a claim that export or deletion endpoints are already implemented.

## Security invariants

- Every operation requires an authenticated learner and derives the target identity from the verified principal. Never accept a user ID from request input as the ownership authority.
- Re-authenticate for destructive actions. Require an explicit confirmation token or equivalent server-issued challenge bound to the authenticated account and a short expiry.
- Return only the caller's data. Apply academy/tenant scoping to every query and export section.
- Do not place passwords, access/refresh tokens, OTPs, provider credentials, or raw authorization headers in exports, logs, analytics, or audit payloads.
- Use HTTPS in production. Exports are private, short-lived artifacts; never expose them through a public bucket or predictable URL.
- Account deletion must revoke sessions before asynchronous cleanup begins. A failed cleanup must not restore login access.

## API contract (both Spring Boot and Laravel)

### Request export
`POST /api/v1/me/data-export`

Authenticated, rate-limited, CSRF-safe where cookie auth is used. Return `202 Accepted` with an opaque request ID and `status: queued`. Duplicate requests for the same active export should reuse or safely supersede the active job.

### Inspect export
`GET /api/v1/me/data-export/{requestId}`

The request ID is not an authorization capability. Verify ownership on every read. States: `queued`, `processing`, `ready`, `expired`, `failed`. A ready response may include a short-lived download URL or stream the file after authorization.

### Request account deletion
`POST /api/v1/me/account-deletion`

Require recent authentication and explicit confirmation. Return `202 Accepted` with an opaque request ID. Immediately revoke active refresh sessions and invalidate access where supported; block new sign-ins for the account while deletion is pending. Do not reveal whether another account exists through unauthenticated lookup.

### Inspect deletion
`GET /api/v1/me/account-deletion/{requestId}`

Ownership-check every request. States: `requested`, `revoked`, `deleting`, `completed`, `blocked`. A blocked state must be operator-actionable without exposing private learner content.

## Export contents and exclusions

Include a versioned JSON archive (and optional human-readable CSV where appropriate) of:
- profile fields that the learner supplied;
- lesson progress and mastery/projection summaries;
- quiz attempts, answers submitted by the learner, scores and review history;
- bookmarks and saved items;
- learner preferences, study goals and reminder settings.

Include a manifest with schema version, generated-at UTC timestamp, included sections, and documented omissions. Exclude credentials, provider tokens, OTP history/secrets, internal risk signals, other learners' data, admin-only notes, and secrets. Avoid exporting content licensed or authored by other parties unless the learner's records require only a stable reference.

## Deletion and retention rules

- Revoke refresh-token families, device sessions, and provider-linked session grants at request acceptance.
- Delete or anonymize learner-owned profile, progress, quiz history, bookmarks, preferences, device tokens, and export artifacts according to the retention schedule approved for production.
- Remove or detach provider identity links so they cannot silently recreate the deleted account.
- Remove queued notifications and pending jobs; suppress future reminders.
- For immutable audit/security records or legally required financial records, retain only the minimum fields for the approved period and document the legal basis. Never retain credentials or learner answer content solely for audit convenience.
- Expire export files automatically; deletion requests must also purge the learner's export artifacts.
- Make cleanup idempotent, resumable, and safe after process restart. Record stage completion, not sensitive row payloads.

## Required verification before marking #195 complete

- Ownership tests: another learner's export/deletion request ID returns 404/403 without data leakage.
- Re-authentication, expired challenge, missing confirmation, rate-limit and replay tests.
- Export schema tests, tenant-isolation tests, and redaction tests.
- Deletion tests cover session revocation first, linked identities, reminder/device-token cleanup, retry after partial failure, and prevention of re-login during pending deletion.
- Equivalent contract tests run against Spring Boot and Laravel; client UI offers export, deletion explanation, explicit confirmation, and status/error recovery.
- Document retention periods, backup expiry behavior, support escalation, and production privacy notice before release.

## Implementation sequence

1. Add shared API contract and stable response/error schema.
2. Implement authenticated export jobs and private artifact lifecycle in the active backend.
3. Implement deletion state machine and session revocation in the active backend.
4. Port contract to the secondary backend and run shared tests.
5. Add learner-facing settings screens and production retention/backup verification.

This slice does not close #195 or #6. Provider account unlinking, rotating refresh-token reuse detection, OTP vendor validation, and the complete cross-platform security audit remain release gates under #6.
