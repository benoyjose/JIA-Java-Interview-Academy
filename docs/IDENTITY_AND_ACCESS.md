# Identity and Access Control

## Shared identity schema

The canonical identity model is introduced by Spring Flyway migration V2 and mirrored by a Laravel migration for environments where Laravel is selected as the active backend. Provider-neutral identity records and OTP challenge storage are added by Spring migration V7 and the matching Laravel migration. Do not run both migration systems against the same environment. Do not run both migration systems against the same environment. Choose one migration owner per deployment.

Tables:
- `users`: UUID, unique email, password hash, account status and verification timestamp.
- `roles`: LEARNER, EDITOR, REVIEWER, ADMIN.
- `permissions`: explicit capability codes.
- `user_roles`: many-to-many role assignment.
- `role_permissions`: role-to-permission mapping.
- `api_tokens`: hashed opaque bearer token, expiry, revocation and last-used timestamps.

### Provider-neutral identity model (required extension)

A JIA user is a single canonical account. Authentication methods are linked identities, not separate user accounts. The `auth_identities` table contains:
- UUID primary key and `user_id` foreign key.
- Provider enum/code: `GOOGLE`, `APPLE`, `PHONE`, `EMAIL`.
- Stable provider subject (Google/Apple `sub`; normalized E.164 phone; normalized email address).
- Provider email and `email_verified` metadata where supplied.
- Created/last-authenticated timestamps and provider metadata JSON where justified.
- Unique constraint on (`provider`, `provider_subject`); index by user_id.

Keep password credentials separate from federated identity records. Do not use email alone as a federated identity key. A provider's immutable subject is the identity key; for phone and email, use verified normalized values. Existing email/password accounts must be migrated or linked without creating duplicate learner profiles.

## Required sign-in methods by platform

| Method | Android | iOS |
|---|---:|---:|
| Google Sign-In (Gmail/Google account) | Yes | Yes |
| Sign in with Apple | No requirement | Yes |
| Phone number + SMS OTP | Yes | Yes |
| Email + password | Yes | Yes |

“Gmail login” means Google Sign-In; JIA must never ask for a user's Google password. iOS must include native Sign in with Apple using AuthenticationServices. When Google is offered on iOS, keep Sign in with Apple available and follow current App Store review requirements.

## Authentication API design

- `POST /auth/federated/google`: exchange a Google ID token (and nonce where applicable) for a JIA session.
- `POST /auth/federated/apple`: exchange an Apple identity token and authorization code/nonce for a JIA session.
- `POST /auth/phone/otp`: request an SMS OTP for an E.164 phone number.
- `POST /auth/phone/verify`: verify the one-time code and issue a JIA session.
- Existing `POST /auth/register` and `POST /auth/login`: email/password registration and login.
- `GET /auth/me` and `POST /auth/logout`: current account and token revocation.
- Account linking/unlinking endpoints must require an authenticated session and fresh proof of control of the identity being added. Prevent removal of the final usable sign-in method.

Provider tokens are credentials for identity verification only; they are not JIA API access tokens. The backend validates signature, issuer, audience, expiry, nonce/state and provider subject against provider metadata/JWKS. Never trust a client-supplied user ID, email verification flag or provider subject without cryptographic verification.

## OTP and account-linking safeguards

- Normalize phone numbers to E.164 and validate country calling codes.
- Generate cryptographically secure OTPs; store only a keyed hash, enforce short expiry, single use, maximum attempts and resend cooldown.
- Rate-limit OTP requests and verification by phone, IP, device and account; add abuse monitoring and SMS spend limits.
- Never expose whether a phone/email is registered through OTP request responses.
- Do not auto-merge accounts just because email addresses match. Require authenticated proof of both accounts or a verified provider flow plus explicit user confirmation.
- Treat Apple's private relay email as a valid address; use Apple's stable subject as the identity key.
- Do not store provider passwords, raw provider access tokens unless strictly needed, raw OTPs, or secrets in mobile binaries/logs.
- Issue JIA session tokens only after successful provider verification. Store mobile credentials in Android Keystore-backed storage and iOS Keychain.

## Permission matrix

| Role | Read catalogue | Edit drafts | Review | Publish | Manage users | Manage releases |
|---|---:|---:|---:|---:|---:|---:|
| LEARNER | Yes | No | No | No | No | No |
| EDITOR | Yes | Yes | No | No | No | No |
| REVIEWER | Yes | No | Yes | No | No | No |
| ADMIN | Yes | Yes | Yes | Yes | Yes | Yes |

## Security requirements

### JIA launch security baseline (keep it proportional)

JIA is a browse-first learning and quick-reference product, not a regulated financial system. Apply strong controls to credentials, personal progress, subscriptions and administration, while keeping published learning content public and friction-free.

- Public endpoints: published catalogue, lessons and public interview-reference content; no learner login required.
- Learner authentication: required only for personal data, synced progress/bookmarks/history, paid entitlements and account settings.
- Learner authorization: each user can access only their own personal records; enforce ownership on the server for every request.
- Admin authorization: keep the small role set (LEARNER, EDITOR, REVIEWER, ADMIN) and enforce it server-side. Require stronger operator account protection for admin access.
- Keep password hashing, provider token verification, secure session storage, OTP expiry/attempt limits, HTTPS, secrets management and payment verification.
- Defer learner MFA, complex account-risk scoring, distributed rate-limiting infrastructure, advanced fraud analytics and elaborate audit dashboards until usage or abuse justifies them.
- Keep basic request throttling on login and OTP endpoints; add infrastructure-level distributed limits only when horizontal scale requires them. Retain SMS spend controls to prevent abuse.
- Record essential admin publishing/role-change audit events; do not build a broad analytics/audit platform for launch.
- Use short-lived access tokens plus revocable refresh sessions for mobile production. Do not add token introspection services or a separate identity microservice.


- Passwords use BCrypt with work factor 12 in both implementations so a user can authenticate after a controlled switch between Spring Boot and Laravel. Never store plaintext passwords.
- Never store raw bearer tokens. Generate high-entropy random tokens, return the raw value once, and persist only SHA-256 hash.
- Expire tokens, support revocation, update last-used timestamps and rate-limit authentication endpoints.
- Never create a default administrator or commit credentials. The first administrator must be provisioned through a one-time, operator-controlled command or secure deployment process.
- Use generic login failure messages to avoid account enumeration.
- Enforce permission checks on every protected server operation.
- Audit role assignments, token revocation and all content publication actions.
- Do not expose password hashes or token hashes in API responses.

## API behavior and implementation status

Implemented: email/password registration and login, current-user lookup and logout in both backends; Google and Apple ID-token verification and session issuance in Spring Boot and Laravel; provider-neutral identity persistence in both backends. Phone OTP request/verify endpoints now exist in both backends. They use E.164 numbers, six-digit codes, five-minute expiry, HMAC-SHA256 digests, per-challenge attempt limits and per-number request throttling. SMS delivery uses a configurable HTTP gateway contract (`to`, `message`, Bearer token); no SMS vendor has been selected or credentials configured. Provider configuration and delivery testing remain deployment tasks. Native Android/iOS sign-in SDK integration, authenticated account linking/unlinking, refresh-token rotation, stronger distributed rate limiting, and complete cross-backend contract tests are pending. Do not enable federated login in production until provider configuration and end-to-end tests are complete. Registration assigns LEARNER only. No public endpoint can assign privileged roles. The first administrator still requires a secure operator-controlled provisioning process. Admin authoring routes and their permission annotations are not yet implemented.
