# JIA Architecture

## Product surfaces

- Android app: Kotlin + Jetpack Compose.
- iOS app: Swift + SwiftUI.
- Backend A: Java 21 + Spring Boot.
- Backend B: PHP 8.3+ + Laravel 13.
- Admin portal: React + TypeScript.
- Shared data platform: PostgreSQL and S3-compatible object storage.

## Deployment strategy: interchangeable backends

Spring Boot and Laravel are two implementations of the same backend contract, not two independent products.

- Maintain feature parity through the versioned OpenAPI contract in `docs/api/openapi.yaml`.
- Keep domain rules, validation, response envelopes, pagination, authorization semantics and error codes equivalent.
- Run one backend as the active production API in the initial phase. Laravel may be selected for the first low-cost deployment; Spring Boot remains buildable and testable as the alternate implementation.
- Do not route requests randomly between implementations.
- Do not allow both backends to independently write to production data at the same time in the initial phase.
- Use one canonical PostgreSQL schema and identical migration semantics. Backend-specific migration files must be reviewed against the shared schema contract.
- Before switching implementations, run the same contract and integration test suite against both backends and take a verified database backup.
- A later controlled comparison can use isolated staging databases and synthetic traffic.

This avoids double hosting and conflicting writes while preserving the option to choose the lower-cost or operationally better implementation.

## Logical architecture

```text
Android (Kotlin / Compose)       iOS (Swift / SwiftUI)
          | HTTPS / JSON / OpenAPI
          +-----------------------------+
                                        |
                         Active API deployment (one)
                         +--------------+--------------+
                         |                             |
                   Laravel API                  Spring Boot API
                   PHP 8.3+                     Java 21
                         |                             |
                         +-------------+---------------+
                                       |
                           PostgreSQL (canonical schema)
                                       |
                              Object storage (S3)

Admin portal (React / TypeScript)
  └── secured admin APIs on the active backend
```

## Technology choices

- Android: Kotlin, Jetpack Compose, MVVM, modular Clean Architecture, Room, DataStore and WorkManager.
- iOS: Swift, SwiftUI, MVVM, SwiftData (or Core Data where deployment requirements demand it), URLSession and background refresh/download capabilities.
- Spring Boot: Java 21, Spring Boot 3.x, Spring Security, validation, Spring Data JPA and Flyway.
- Laravel: PHP 8.3+, Laravel 13, Laravel Sanctum for API token/session strategy as appropriate, Eloquent and Laravel migrations.
- Database: PostgreSQL.
- Admin: React + TypeScript; generated API client from OpenAPI.
- Build and delivery: Gradle, Composer, Docker and GitHub Actions.
- Artifact storage: S3-compatible object storage; store object metadata and checksums in PostgreSQL.

## Backend module boundaries

Both backends implement the same modular-monolith boundaries:

- identity and access
- catalogue
- learning
- assessment
- progress
- content publishing
- content synchronization
- app release
- audit

Keep domain logic separate from HTTP controllers and persistence details. Avoid premature microservices.

## API conventions

Base path: `/api/v1`

Initial resource groups:
- `/auth`
- `/technologies`, `/categories`
- `/lessons`
- `/questions`, `/quizzes`, `/attempts`
- `/progress`, `/bookmarks`
- `/content/manifest`, `/content/sync`
- `/admin/content`, `/admin/releases`
- `/app/releases/latest`, `/app/releases/{version}/download`

The OpenAPI document is the cross-backend and cross-client contract. Use pagination for collection endpoints, stable error envelopes, request validation and server-side role enforcement.

## Identity and client support

- Support email/password and configurable social sign-in providers without coupling user identity to a single mobile platform.
- Use short-lived access tokens and rotating/revocable refresh tokens for native mobile clients.
- Store tokens using Android Keystore-backed encrypted storage and iOS Keychain.
- Admin roles are enforced by the backend, not by hiding UI controls.
- Keep platform-specific purchase/subscription handling outside core learning domain rules; validate entitlements server-side.

## Offline and content updates

**Learning content:** clients fetch a manifest, download versioned content, verify checksums and atomically update local caches. Content updates must not require an app-store release.

**Android application:** publish version name, version code, minimum supported version, release notes, artifact URI and SHA-256 checksum. APK installation uses the Android package installer. Silent installation is not assumed; it requires managed-device/device-owner provisioning.

**iOS application:** distribute through Apple App Store/TestFlight. App binaries are not silently downloaded and installed by the app. Learning content can still update independently through the content API.

## Security baseline

- TLS for all network traffic.
- Short-lived access tokens and secure refresh-token handling.
- Adaptive password hashing.
- Role-based authorization for admin operations.
- Rate limits on authentication and sensitive endpoints.
- Validate uploads by size, content type and checksum; do not trust filenames.
- Keep secrets outside source control.
- Audit publishing, role changes and app release actions.
- Apply platform secure storage for mobile credentials.

## Testing strategy

- Shared OpenAPI contract tests run against both backends.
- Unit tests for domain rules and content validation.
- Repository/database integration tests against PostgreSQL.
- API tests for authorization, publishing and synchronization.
- Android ViewModel, repository and UI tests.
- iOS model, repository, networking and UI tests.
- Admin component and API contract tests.
- End-to-end smoke test: author lesson → approve → publish → Android/iOS sync → offline read.
