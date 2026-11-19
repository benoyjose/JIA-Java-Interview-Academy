# JIA Android Application

## First implementation slice

A native Kotlin/Jetpack Compose application is now scaffolded under this directory.

- Public Java technology catalogue screen.
- Technology → category → lesson list → lesson detail navigation.
- Published lesson detail retrieval by slug, with summary, difficulty, provenance label and current content payload.
- Loads published technology tracks from `GET /api/v1/technologies`.
- No login wall for catalogue browsing.
- Clear loading, empty and API-configuration/error states.
- HTTPS-only network policy; no hardcoded production API host or credentials.

## Local setup

1. Open the `android/` directory in Android Studio.
2. Use the stable Android Studio / Android SDK combination compatible with the pinned Gradle and Android Gradle Plugin versions.
3. Configure the API base URL in your user-level Gradle properties file (not in Git):

```properties
JIA_API_BASE_URL=https://your-jia-api-host
```

The configured value is the host root, without the `/api/v1` suffix. For a local emulator, use a reachable HTTPS development endpoint or configure a dedicated debug-only network policy; do not enable cleartext traffic in release builds.

4. Sync Gradle and run the `app` configuration on an Android 8.0+ device/emulator.

## Application identity

The current application ID `com.jia.academy.app` is a development placeholder. Confirm the final Play Store application ID before configuring Google OAuth, Firebase, signing keys or production release automation.

## Toolchain

See the version catalogue in `gradle/libs.versions.toml`. The versions are aligned to the repository's current documented baseline but must be verified against the official Android Gradle Plugin / Gradle / Kotlin compatibility matrix before a release build.

## Architecture and next slices

Keep the app feature-oriented and MVVM-friendly. The first browse flow is implemented. Email sign-in and learner registration are now connected to the API. Access tokens are encrypted with AES-GCM using a non-exportable Android Keystore key. Public lesson browsing remains available without an account. Next implement session restoration and revocation, Google/phone sign-in, quiz taking, bookmarks and learner progress. The current detail screen displays the API's `contentJson` payload as returned; it does not yet interpret a canonical lesson-block schema. Authentication must never be required to read published content.

The target architecture remains Kotlin + Jetpack Compose, Room, DataStore, WorkManager, and Keystore-backed credential storage. Do not put provider secrets in the app binary.

## API contract

Use `docs/api/openapi.yaml`. Do not hardcode backend-specific behavior or duplicate server-side business rules in the app.


## Quiz difficulty selection

The app includes a public quiz-selection screen with Beginner, Novice, Professional and Expert levels. It queries published question availability without requiring sign-in. Email registration and login are wired to the backend, with the issued access token stored encrypted on-device. Quiz start, answering, submission and performance history still need to be connected to the Android client.
