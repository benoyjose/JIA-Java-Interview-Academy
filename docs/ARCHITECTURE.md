# JIA Architecture

## Product surfaces

- Android: Kotlin + Jetpack Compose.
- iOS: Swift + SwiftUI.
- Backend A: Java 25 LTS + Spring Boot 4.1.2.
- Backend B: PHP 8.5 + Laravel 13.x.
- Admin portal: React 19 + TypeScript.
- Shared data platform: PostgreSQL 18 and S3-compatible object storage.

## Latest stable toolchain policy

Use the latest stable, non-preview release at implementation time, and pin exact tool versions in build files and CI. Prefer an LTS runtime where one exists for production; do not adopt beta, release-candidate or canary dependencies in production without an explicit decision.

Current baseline checked 19 November 2026:
- Java 25 LTS; Spring Boot 4.1.2.
- PHP 8.5; Laravel 13.x.
- Gradle 9.9.1.
- Kotlin 2.4.x; Android Gradle Plugin 9.5.x; Jetpack Compose BOM 2026.11.01.
- Xcode 27.1 stable; Swift 6.4; iOS 27 SDK.
- React 19.3.x; Node.js 26.x LTS/current line.
- PostgreSQL 18.x.
- Docker Engine 29.x.

Patch versions should be refreshed from official release channels when a build is created; avoid floating  dependency versions. The version constraints in Composer should allow compatible patch updates, with a committed lockfile once dependency resolution is performed.

## Backend strategy: Spring Boot primary, Laravel secondary

Spring Boot is the sole planned production API for the initial JIA release. Laravel is retained as a secondary implementation for compatibility, reference and a possible future backend switch; it is not deployed alongside Spring Boot.

- Implement new product capabilities in Spring Boot first.
- Keep `docs/api/openapi.yaml` accurate for the production Spring API and clients.
- Do not block product delivery on implementing every feature twice.
- Update Laravel when a backend migration is planned or when a specific shared contract needs to remain compatible.
- Keep one canonical PostgreSQL database and one production writer.
- Never split production traffic or allow both backends to write independently.
- Before switching to Laravel, complete the missing feature set, run contract/integration tests against the candidate backend, verify migration compatibility and take a tested database backup.

## Logical architecture

```text
Android (Kotlin / Compose)       iOS (Swift / SwiftUI)
          | HTTPS / JSON / OpenAPI
          +-----------------------------+
                                        |
                         Spring Boot API (production)
                         Java 25 / Spring Boot
                                  |
                         PostgreSQL 18
                         (canonical database)

              Laravel API (secondary; not deployed in parallel)
                    PHP 8.5 / Laravel 13
                                       |
                              Object storage (S3)

Admin portal (React / TypeScript)
  └── secured admin APIs on the active backend
```

## Backend module boundaries

Both backends implement the same modular-monolith boundaries: identity, catalogue, learning, assessment, progress, content publishing, synchronization, app release and audit. Keep domain logic separate from HTTP and persistence layers; avoid premature microservices.

## API conventions

Base path: `/api/v1`. The OpenAPI document is the normative HTTP contract for the production Spring API and all clients. Use stable error envelopes, validation, pagination and server-side authorization. Laravel is not a release gate for ordinary Spring-first product increments.

## Identity and client support

- Public published learning content is anonymous/read-only; authentication is required only for personal data, saved progress, subscriptions and admin functions.
- Keep the learner authentication flow lightweight. Do not introduce an external identity microservice or enterprise IAM suite for launch.
- Retain basic login/OTP throttling and server-side ownership checks. Defer advanced fraud/risk infrastructure until justified by usage.

## Identity and client support

- Support email/password and configurable identity providers.
- Use short-lived access tokens and rotating/revocable refresh tokens for native clients.
- Store tokens using Android Keystore-backed storage and iOS Keychain.
- Enforce admin roles server-side.
- Validate subscription entitlements server-side.

## Offline and content updates

Clients fetch a manifest, download versioned content, verify checksums and atomically update local caches. Content updates do not require app releases.

Android APK updates use the package installer; silent installation requires managed-device provisioning. iOS binaries are distributed through App Store/TestFlight; app binaries are not silently installed by the app.

## Security and quality

Keep the launch baseline practical: TLS, adaptive password hashing, basic login/OTP rate limits, input validation, secret management, server-side role checks and ownership checks. Defer enterprise IAM, complex permission matrices, extensive audit infrastructure and duplicate cross-backend security suites until a concrete requirement exists. Run focused CI and integration tests for the active Spring Boot backend. Maintain Laravel's compatibility tests when that implementation is changed or prepared for a backend switch.
