# JIA Mobile Applications: Android and iOS

Both mobile applications provide the same learner capabilities with platform-native presentation.

## Shared functional scope

- Authentication and account
- Home dashboard, daily goal and learning streak
- Technology tracks, categories and lesson reader
- Concise interview answer first; expandable explanation and code
- Search, bookmarks and quick revision
- Quizzes, timed mock interviews and retry incorrect questions
- Progress, strengths and weak-area practice
- Offline lesson library and background content synchronization
- Notifications/reminders with user-controlled settings

## Platform implementation

### Android
Kotlin, Jetpack Compose, MVVM, Room, DataStore, WorkManager, Android Keystore and Android package-installer update flow.

### iOS
Swift, SwiftUI, MVVM, SwiftData/Core Data behind repository interfaces, URLSession, Keychain, BackgroundTasks and background URLSession. App binary distribution is through App Store/TestFlight; content remains independently updatable.

## Cross-platform parity

- Both clients consume the same versioned OpenAPI contract.
- Share API models through generated clients where practical, but keep native UI and platform integrations.
- Maintain a feature parity checklist and contract tests.
- Preserve offline learner activity across content refreshes; synchronize queued writes idempotently.
- Accessibility, dynamic text sizing, dark mode and readable code blocks are release requirements.
