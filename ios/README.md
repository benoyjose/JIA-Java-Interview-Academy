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
