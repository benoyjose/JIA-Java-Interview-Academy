# JIA iOS Application

## Current stable toolchain baseline

- Xcode 27.1 stable (do not use Xcode 27.3 beta for production builds).
- Swift 6.4 compiler; Swift 6 language mode.
- iOS 27 SDK.
- Set minimum deployment target based on product analytics and device support; Xcode 27 supports iOS 17 and later for development/device debugging.

## Architecture

Deliver the JIA learner experience as a native iOS application with feature parity to Android while respecting iOS interaction and distribution conventions.

- SwiftUI for presentation.
- MVVM with feature-oriented modules and repository interfaces.
- URLSession for API communication.
- SwiftData for local persistence where supported by the chosen minimum iOS version; use a repository abstraction so Core Data can be substituted if needed.
- Keychain for access/refresh token storage.
- BackgroundTasks and background URLSession for opportunistic content refresh/downloads.
- XCTest and XCUITest.

## Initial modules

App shell and navigation, authentication, home dashboard, technology catalogue, lesson reader, code and interview-answer presentation, quiz and mock interview, bookmarks and search, progress and weak-area insights, offline content library, settings and sync diagnostics.

## Offline behavior

Cache published lesson content and metadata locally. Save eligible learner activity offline and queue it for later synchronization. Use stable content IDs and revision numbers; verify checksums before activating a downloaded revision. Never overwrite queued learner activity during content refresh.

## Distribution

App binary updates are delivered through App Store/TestFlight. Learning content and question-bank updates remain independent of app releases. Subscription entitlements must be validated server-side and comply with applicable Apple rules.

The iOS app uses the shared OpenAPI contract and must not duplicate backend business rules.

## Authentication increment

The native app shell now exposes an account screen using the shared Spring authentication contract:

- Email/password sign-in uses `POST /api/v1/auth/login`.
- Access tokens are held in memory; the rotating refresh token is stored in Keychain.
- Session restoration rotates the refresh token and prevents concurrent restore calls from racing.
- Sign-out revokes the current session through the backend and clears local credentials.
- Public catalogue and lesson browsing remains available without authentication.

This is a first parity slice, not full iOS parity. Quiz/mock assessment, learner progress/bookmarks, offline content sync and the maintained parity checklist remain separate follow-up work.

## Assessment parity increment

The native SwiftUI quiz feature now consumes the shared assessment API for practice, timed mock, daily challenge and weak-area modes. It supports objective selection, free-response coding/SQL/debugging/scenario prompts, result/rubric display, history, question count and timed-mock duration. The timed mock uses the server deadline and automatically submits at expiry.

This is an incremental feature parity slice. Resume/abandon and filtered topic selection are being aligned with the current backend PR; progress/bookmarks and offline sync remain outstanding.

### Assessment continuity

The iOS assessment client now supports technology/category-filtered question selection, filtered availability, restoring saved single/multi-select and text responses from an in-progress attempt, resuming today's active daily challenge, and abandoning attempts from history. These operations use the same authenticated API and ownership/status rules as Android.

### Learner continuity

The lesson reader now loads the authenticated learner's progress and bookmarks, exposes an explicit progress save control, and supports idempotent bookmark add/remove using the shared backend routes. The API client handles empty successful responses such as HTTP 204 bookmark removal.

### Offline content release

The app shell can download the latest immutable content release. The iOS store verifies every lesson's content revision and SHA-256 checksum, writes a staging release, and changes the active-release pointer only after the entire manifest is validated. A failed or interrupted download leaves the previously active release readable. Lesson detail falls back to verified cached content when the network request fails.

This increment covers content cache/download activation; offline queued progress/bookmark mutation and retry/backoff remain separate parity work.

### Offline learner activity

Progress and bookmark changes are first written to an atomic local queue, coalesced by lesson, and then synchronized when a valid session/network is available. Acknowledgements remove only the exact queued snapshot sent, so a newer local edit cannot be lost to an older in-flight request. The queue is retried on app launch and through the Offline sync action. A prior sign-in is required to queue authenticated learner changes; refresh credentials are retained across transport failures.
