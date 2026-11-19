# JIA Laravel API

Laravel 13.x / PHP 8.5 backend implementation of the JIA shared API contract.

## Requirements

- PHP 8.5 (latest stable patch)
- Composer 2.x
- PostgreSQL 18.x
- Laravel 13.x

## Setup

1. Install dependencies: `composer install`.
2. Copy `.env.example` to `.env` and configure PostgreSQL.
3. Generate an application key: `php artisan key:generate`.
4. Point the application to a database initialized with the canonical JIA schema.
5. Start locally: `php artisan serve`.

The initial Laravel slice reads the shared catalogue tables. It does not introduce a divergent schema or invent lesson content. Laravel migrations equivalent to the canonical schema and automated parity checks are required before production use.

## Authentication routes

- `POST /api/v1/auth/register` — creates a LEARNER account and issues an opaque bearer token.
- `POST /api/v1/auth/login` — authenticates an active account.
- `POST /api/v1/auth/federated/google` and `/api/v1/auth/federated/apple` — verify provider ID tokens and issue a JIA session.
- `POST /api/v1/auth/phone/otp` and `/api/v1/auth/phone/verify` — request and verify a six-digit SMS code.
- `GET /api/v1/auth/me` — returns the authenticated account.
- `POST /api/v1/auth/logout` — revokes the current token.

Passwords use BCrypt work factor 12 for compatibility with Spring Boot. Tokens are 256-bit random opaque bearer values, stored as SHA-256 hashes, and expire after 30 days. Registration cannot assign privileged roles. Google and Apple sign-in require `GOOGLE_OAUTH_CLIENT_ID` and `APPLE_SERVICE_ID`. Phone login requires `JIA_SMS_ENDPOINT`, `JIA_SMS_TOKEN` and `JIA_OTP_HMAC_SECRET`; the gateway contract is documented in [External Configuration Setup](../docs/EXTERNAL_CONFIGURATION_SETUP.md).

## Catalogue routes

- `GET /api/v1/technologies`
- `GET /api/v1/technologies/{slug}`
- `GET /api/v1/technologies/{technologySlug}/categories`
- `GET /api/v1/categories/{categorySlug}/lessons`
- `GET /api/v1/lessons/{slug}`

Only `PUBLISHED` records are returned. Response fields are defined in `docs/api/openapi.yaml`.

## Current limitations

Native Android/iOS provider SDK integrations, authenticated account linking/unlinking, rotating refresh tokens, admin authoring, assessments, progress, content synchronization, audit and release management remain unimplemented. Do not enable phone login in production until an SMS vendor is selected and end-to-end delivery tests pass. Do not run both backend implementations as simultaneous production writers.
