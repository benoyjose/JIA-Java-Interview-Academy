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

## Interchangeable backend deployment

Spring Boot and Laravel are two implementations of the same backend contract, not two independent products.

- Maintain feature parity through `docs/api/openapi.yaml`.
- Keep domain rules, validation, response envelopes, pagination, authorization semantics and error codes equivalent.
- Run one backend as the active production API in the initial phase. Select Laravel or Spring Boot after cost and operational evaluation.
- Do not route requests randomly between implementations.
- Do not allow both backends to independently write to production data at the same time in the initial phase.
- Use one canonical PostgreSQL schema and equivalent migration semantics.
- Before switching implementations, run the same contract and integration tests against both and take a verified database backup.

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
                   PHP 8.5                      Java 25 / Boot 4.1
                         |                             |
                         +-------------+---------------+
                                       |
                           PostgreSQL 18 (canonical schema)
                                       |
                              Object storage (S3)

Admin portal (React / TypeScript)
  └── secured admin APIs on the active backend
```

## Backend module boundaries

Both backends implement the same modular-monolith boundaries: identity, catalogue, learning, assessment, progress, content publishing, synchronization, app release and audit. Keep domain logic separate from HTTP and persistence layers; avoid premature microservices.

## API conventions

Base path: `/api/v1`. The OpenAPI document is the normative HTTP contract for both backends and all clients. Use stable error envelopes, validation, pagination and server-side authorization.

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

TLS, adaptive password hashing, rate limits, upload validation, secret management and audit trails are mandatory. Run the same API contract suite against both backend implementations and test the full author → review → publish → mobile sync → offline read flow.
