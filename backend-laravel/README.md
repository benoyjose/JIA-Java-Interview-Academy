# JIA Laravel API — Secondary Implementation

Laravel 13.x / PHP 8.5 is retained as a secondary compatibility/reference implementation. Spring Boot is the primary production backend. Do not deploy Laravel alongside Spring Boot or use it as an independent production writer. Implement new product capabilities in Spring Boot first; update Laravel when a backend switch is planned or a specific compatibility requirement justifies it.

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


## Difficulty-based quizzes

The API includes four question difficulty values: `BEGINNER`, `NOVICE`, `PROFESSIONAL` and `EXPERT`. Public clients can preview published question counts with `GET /api/v1/quizzes/availability?difficulty=BEGINNER`. Authenticated learners can start attempts with `POST /api/v1/quiz-attempts`, submit with `POST /api/v1/quiz-attempts/{attemptId}/submit`, view `GET /api/v1/me/quiz-performance` and inspect `GET /api/v1/me/quiz-attempts`. Only published question revisions are eligible; answer keys are withheld until submission. No question bodies are seeded by the schema migration; reviewed questions must be authored/imported before the availability counts become non-zero.
