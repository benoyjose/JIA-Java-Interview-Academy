# Learner Data Lifecycle and Privacy Controls

This issue's implementation contract defines minimum privacy behavior. It is not a claim that export/deletion endpoints currently exist.

## Data export
- Require a valid authenticated session and re-authentication for sensitive export initiation.
- Export only the requesting account's profile, learning progress, bookmarks, quiz attempts/responses, preferences and subscription references.
- Exclude password hashes, access/refresh tokens, provider secrets, internal moderation notes and other learners' data.
- Produce a versioned JSON archive; identify generation time and schema version.
- Use an expiring, single-use download capability; never place the archive in a public bucket. Audit initiation and completion without logging archive contents.

## Account deletion
- Require explicit confirmation and recent authentication; provide a clear irreversible-action warning.
- Revoke active sessions and refresh tokens immediately.
- Delete or irreversibly anonymize user-owned profile and learning records according to documented legal/financial retention requirements.
- Cascade deletion must be reviewed against FK dependencies; payment/audit records that must be retained are minimized and pseudonymized.
- Make the process idempotent and asynchronous for large accounts. Return a deletion request ID and status; do not promise immediate physical erasure from backups.
- Define backup expiry and restore-time deletion replay so deleted accounts are not resurrected.

## Authorization and tenancy
- Derive account identity from the authenticated principal, never request parameters.
- Scope every query and export to the user's academy memberships.
- Admin access is role-gated, purpose-limited and audited.

## API and client behavior
- Define export request/status/download and deletion request/status contracts.
- Responses expose stable states (REQUESTED, PROCESSING, READY, FAILED, COMPLETED) and safe error codes.
- Android and iOS clients clear local credentials and cached private data after deletion confirmation.

## Verification
Integration tests must prove cross-user export denial, academy isolation, token revocation, repeat deletion idempotency, and that secrets/other-user data never appear in archives.