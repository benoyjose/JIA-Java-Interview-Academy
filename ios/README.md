# JIA iOS Application

## Goal

Deliver the JIA learner experience as a native iOS application with feature parity to Android while respecting iOS interaction and distribution conventions.

## Technology

- Swift 6-compatible codebase; pin the exact compiler/toolchain in CI when the Xcode project is created.
- SwiftUI for presentation.
- MVVM with feature-oriented modules and repository interfaces.
- URLSession for API communication.
- SwiftData for local persistence where supported by the chosen minimum iOS version; use a repository abstraction so Core Data can be substituted if needed.
- Keychain for access/refresh token storage.
- BackgroundTasks and background URLSession for opportunistic content refresh/downloads.
- XCTest and XCUITest.

## Initial modules

- App shell and navigation
- Authentication and account
- Home dashboard
- Technology catalogue
- Lesson reader
- Code and interview-answer presentation
- Quiz and mock interview
- Bookmarks and search
- Progress and weak-area insights
- Offline content library
- Settings and sync diagnostics

## Offline behavior

- Cache published lesson content and required metadata locally.
- Save quiz attempts locally when offline and queue eligible submissions for later synchronization.
- Use stable content IDs and revision numbers to prevent duplicate or stale updates.
- Verify content checksums before activating a downloaded revision.
- Never overwrite locally queued learner activity during content refresh.

## iOS distribution constraints

- App binary updates are delivered through the App Store/TestFlight, not by silently downloading and installing an IPA from inside the app.
- Learning content and question-bank updates remain independent of app releases.
- In-app purchases and subscription entitlements must be validated server-side and comply with applicable Apple rules.

## Delivery stages

1. Xcode project, app shell, design tokens and navigation.
2. API client, authentication and secure token storage.
3. Catalogue, lesson reader and offline cache.
4. Quizzes, bookmarks, progress and search.
5. Sync conflict handling, accessibility, analytics and release automation.

The iOS app must use the shared OpenAPI contract. It must not contain a second copy of business rules that belong on the backend.
