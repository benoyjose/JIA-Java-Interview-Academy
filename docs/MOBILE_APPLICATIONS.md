# JIA Mobile Applications: Android and iOS

Both mobile applications provide the same learner capabilities with platform-native presentation and a shared account identity across platforms.

## Authentication requirements

| Sign-in method | Android | iOS |
|---|---:|---:|
| Google Sign-In (Gmail/Google account) | Required | Required |
| Sign in with Apple | Not required | Required |
| Phone number + OTP | Required | Required |
| Email + password | Required | Required |

**Terminology:** “Gmail login” on iOS is implemented as Google Sign-In using a Google account. It is not a separate Gmail-password authentication mechanism. Users authenticate with Google's official SDK/credential flow; JIA receives and validates an ID token on the backend.

### Native implementation

- **Android:** Credential Manager / Google Identity integration for Google Sign-In; phone number entry with SMS one-time passcode; email/password registration and login.
- **iOS:** Google Sign-In; native Sign in with Apple using AuthenticationServices; phone number entry with SMS one-time passcode; email/password registration and login.
- On iOS, present Sign in with Apple prominently alongside Google when offering Google as a third-party sign-in option, and follow current App Store review requirements.
- Do not collect or store Google or Apple account passwords. Never embed provider client secrets in mobile binaries.
- Use authorization-code/identity-token flows supported by the provider SDKs. Backend validates issuer, audience, signature, expiry, nonce/state and provider subject before issuing JIA tokens.
- Phone numbers use E.164 normalization. OTPs are short-lived, single-use, attempt-limited and rate-limited by phone number, IP and device. Never log or persist OTP values in plaintext.
- Email/password uses verified email ownership, generic login errors, password hashing and throttling.
- Support safe account linking across methods. Do not automatically merge accounts solely because email strings match; require proof of control of both identities or a verified provider assertion and explicit confirmation. Handle Apple's private relay addresses as valid provider identities.
- One JIA account must retain the same learner progress, subscriptions and history regardless of sign-in method or device. Store provider identities separately from the user record and allow multiple identities to link to one user.
- Provide logout and server-side token revocation. Native clients store issued credentials securely (Android Keystore-backed storage; iOS Keychain).

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
