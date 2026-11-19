# JIA Architecture

## Logical architecture

```text
Android app (Kotlin / Jetpack Compose)
  ├── presentation: Compose screens + ViewModels
  ├── domain: use cases + domain models
  ├── data: repositories + API/Room implementations
  └── offline cache: Room + DataStore + WorkManager
                 |
                 | HTTPS / JSON (OpenAPI)
                 v
Backend API (Java 21 / Spring Boot)
  ├── identity and access
  ├── technology catalogue and lessons
  ├── questions, quizzes and attempts
  ├── progress, bookmarks and search
  ├── content release and synchronization
  └── app release metadata
       |                 |
       v                 v
PostgreSQL        Object storage
                  (media and APK artifacts)

Admin portal (React / TypeScript)
  └── uses secured admin APIs for authoring, review and publishing
```

## Technology choices

- Android: Kotlin, Jetpack Compose, MVVM, modular Clean Architecture, Room, DataStore and WorkManager.
- Backend: Java 21, Spring Boot, Spring Security, validation, Spring Data JPA and Flyway.
- Database: PostgreSQL.
- Admin: React + TypeScript; use a maintainable component system and generated API client from OpenAPI.
- Build and delivery: Gradle, Docker and GitHub Actions.
- Artifact storage: S3-compatible object storage; store object metadata and checksums in PostgreSQL.

## Backend module boundaries

Start as a modular monolith to reduce operational overhead. Keep clear package boundaries so modules can be extracted later if scale and team ownership justify it.

- identity
- catalogue
- learning
- assessment
- progress
- contentpublishing
- apprelease
- audit

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

Use pagination for collection endpoints, stable error response envelopes, request validation and OpenAPI documentation. Admin endpoints must enforce roles on the server, not only hide controls in the UI.

## Update channels

**Learning content:** manifest polling, incremental downloads, checksum validation and local cache replacement. This must not require an APK update.

**Android application:** publish version name, version code, minimum supported version, release notes, artifact URI and SHA-256 checksum. Android downloads the APK and uses the Android package installer flow. Silent installation is not assumed; it requires an appropriately managed device/device-owner deployment.

## Security baseline

- TLS for all network traffic.
- Short-lived access tokens and secure refresh-token handling.
- Password hashing using a modern adaptive algorithm.
- Role-based authorization for admin operations.
- Rate limits on authentication and sensitive endpoints.
- Validate uploads by size, content type and checksum; do not trust filenames.
- Keep secrets outside source control.
- Audit publishing, role changes and app release actions.

## Testing strategy

- Unit tests for domain rules and content validation.
- Repository and database integration tests using disposable PostgreSQL.
- API integration tests for authorization, publishing and synchronization.
- Android ViewModel, repository and UI tests.
- Admin component and API contract tests.
- End-to-end smoke test: author lesson → approve → publish → Android sync → offline read.
