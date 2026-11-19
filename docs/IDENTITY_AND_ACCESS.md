# Identity and Access Control

## Shared identity schema

The canonical identity model is introduced by Spring Flyway migration V2 and mirrored by a Laravel migration for environments where Laravel is selected as the active backend. Do not run both migration systems against the same environment. Choose one migration owner per deployment.

Tables:
- `users`: UUID, unique email, password hash, account status and verification timestamp.
- `roles`: LEARNER, EDITOR, REVIEWER, ADMIN.
- `permissions`: explicit capability codes.
- `user_roles`: many-to-many role assignment.
- `role_permissions`: role-to-permission mapping.
- `api_tokens`: hashed opaque bearer token, expiry, revocation and last-used timestamps.

## Permission matrix

| Role | Read catalogue | Edit drafts | Review | Publish | Manage users | Manage releases |
|---|---:|---:|---:|---:|---:|---:|
| LEARNER | Yes | No | No | No | No | No |
| EDITOR | Yes | Yes | No | No | No | No |
| REVIEWER | Yes | No | Yes | No | No | No |
| ADMIN | Yes | Yes | Yes | Yes | Yes | Yes |

## Security requirements

- Passwords must be hashed with Argon2id where supported; use bcrypt only when Argon2id is unavailable and with a current work factor.
- Never store raw bearer tokens. Generate high-entropy random tokens, return the raw value once, and persist only SHA-256 hash.
- Expire tokens, support revocation, update last-used timestamps and rate-limit authentication endpoints.
- Never create a default administrator or commit credentials. The first administrator must be provisioned through a one-time, operator-controlled command or secure deployment process.
- Use generic login failure messages to avoid account enumeration.
- Enforce permission checks on every protected server operation.
- Audit role assignments, token revocation and all content publication actions.
- Do not expose password hashes or token hashes in API responses.

## API behavior

Authentication endpoints will be versioned under `/api/v1/auth`. The initial schema does not yet imply that login, registration, token issuance or protected admin APIs are implemented. Those are the next coding slice, and must be implemented with equivalent behavior in both backend applications.
