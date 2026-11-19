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

- Passwords use BCrypt with work factor 12 in both implementations so a user can authenticate after a controlled switch between Spring Boot and Laravel. Never store plaintext passwords.
- Never store raw bearer tokens. Generate high-entropy random tokens, return the raw value once, and persist only SHA-256 hash.
- Expire tokens, support revocation, update last-used timestamps and rate-limit authentication endpoints.
- Never create a default administrator or commit credentials. The first administrator must be provisioned through a one-time, operator-controlled command or secure deployment process.
- Use generic login failure messages to avoid account enumeration.
- Enforce permission checks on every protected server operation.
- Audit role assignments, token revocation and all content publication actions.
- Do not expose password hashes or token hashes in API responses.

## API behavior

Implemented authentication endpoints: `POST /api/v1/auth/register`, `POST /api/v1/auth/login`, `GET /api/v1/auth/me` and `POST /api/v1/auth/logout`. Tokens are opaque, 256-bit random bearer tokens with a 30-day expiry; only SHA-256 token hashes are stored. Registration assigns LEARNER only. No public endpoint can assign privileged roles. The first administrator still requires a secure operator-controlled provisioning process. Admin authoring routes and their permission annotations are not yet implemented.
