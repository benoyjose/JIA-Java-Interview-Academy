# iOS delivery and parity register — issue #8

The repository currently exposes `JIAClient` as a Swift Package library. A package library is **not an installable iOS application target**; package compilation alone must not be reported as App Store/TestFlight readiness.

| Capability | Source evidence | Delivery status |
|---|---|---|
| SwiftUI learner shell/catalogue | `JIAAppShell.swift`, `JIAExploreViews.swift` | reusable components; native app target/launch config missing |
| API/session | `JIAAPIClient.swift`, `JIAAuthSession.swift` | package code; provider SDK/app wiring needs verification |
| Lesson reader | `JIALessonReaderView.swift`, `JIALessonContent.swift` | component-level |
| Quiz/practice | `JIAQuizView.swift`, `JIASessionRunner.swift`, `JIAPracticeQueue.swift` | component-level |
| Offline content/activity | `JIAOfflineContentStore.swift`, `JIAOfflineActivityQueue.swift` | policies and package tests; background end-to-end sync outstanding |
| Study plan/mastery/review | study-plan, mastery and spaced-review sources | component-level |
| Reminders | `JIAStudyReminderPolicy.swift` | policy only; permission/delivery adapter outstanding |
| Accessibility/release | Swift package tests | XCUITest, signing, archive, TestFlight and release workflow outstanding |

## Completion gate
1. Commit a native Xcode application target, launch entry and environment-safe dependency/configuration.
2. Build/archive with a documented Xcode + iOS SDK.
3. Test anonymous catalogue, sign-in, lesson, quiz, offline download, queued progress and recovery.
4. Add critical-flow UI tests, VoiceOver/dynamic type checks and lifecycle cancellation coverage.
5. Configure signing only through protected CI secrets.
6. Track platform gaps honestly; source-file presence is not feature parity.
