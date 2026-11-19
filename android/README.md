# JIA Android Application

## Current stable toolchain baseline

- Android Studio Rabbit 2 stable.
- Android Gradle Plugin 9.5.0.
- Gradle 9.9.1 or the latest compatible stable patch.
- Kotlin 2.4.x (use the stable 2.4.21 line; do not use 2.5 release candidates).
- Jetpack Compose BOM 2026.11.01 (Compose 1.13 stable line).
- Compile SDK 37.1 where supported by the selected stable Android SDK installation.
- Kotlin Compose compiler Gradle plugin aligned to the Kotlin compiler version.

Recheck official compatibility matrices before creating the Android build; AGP, Gradle, Kotlin and JDK versions must be a compatible set.

## Architecture

Kotlin and Jetpack Compose; MVVM with feature-oriented Clean Architecture; Room for offline catalogue and learner data; DataStore for preferences; WorkManager for deferred synchronization; Retrofit/OkHttp or Ktor Client generated/configured from the shared OpenAPI contract; Android Keystore-backed secure token storage.

## Initial modules

App shell/navigation, authentication, home dashboard, technology catalogue, lesson reader, interview-answer cards, quizzes, bookmarks/search, progress, offline library and settings.

## Offline and release behavior

Cache published lesson content and metadata locally. Verify manifest revisions and checksums before activating downloaded content. Queue eligible quiz/progress writes while offline and retry safely with idempotency keys. Keep content updates independent of APK updates. APK updates use the Android package installer; silent install is only available in appropriately managed/device-owner deployments.

## API contract

Use `docs/api/openapi.yaml`. Do not hardcode backend-specific behavior or duplicate server-side business rules in the app.
