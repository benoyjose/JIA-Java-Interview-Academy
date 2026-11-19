# JIA Android Application

## Technology

- Kotlin and Jetpack Compose.
- MVVM with feature-oriented Clean Architecture.
- Room for offline catalogue and learner data.
- DataStore for preferences.
- WorkManager for deferred content synchronization.
- Retrofit/OkHttp (or Ktor Client) generated/configured from the shared OpenAPI contract.
- Android Keystore-backed secure token storage.
- JUnit, coroutine tests and Compose UI tests.

## Initial modules

App shell/navigation, authentication, home dashboard, technology catalogue, lesson reader, interview-answer cards, quizzes, bookmarks/search, progress, offline library and settings.

## Offline and release behavior

- Cache published lesson content and metadata locally.
- Verify manifest revisions and checksums before activating downloaded content.
- Queue eligible quiz/progress writes while offline and retry safely with idempotency keys.
- Keep content updates independent of APK updates.
- APK updates use the Android package installer; silent install is only available in appropriately managed/device-owner deployments.

## API contract

Use `docs/api/openapi.yaml`. Do not hardcode backend-specific behavior or duplicate server-side business rules in the app.
