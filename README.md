# JIA — Java Interview Academy

JIA is the first separately marketed academy app in a family of interview-preparation products, built on one shared platform.

## Product family
- **JIA:** Java Interview Academy — first launch.
- **PIA:** Python Interview Academy — future separate app.
- **GIA:** Go Interview Academy — future separate app.

Each academy will have distinct Android and iOS store listings, branding, app names, icons and package/bundle identifiers. The apps share code modules and connect to one backend platform, one canonical database and one central admin portal. The admin team can manage each academy independently and view consolidated reporting.

## Product surfaces
- Android apps: Kotlin + Jetpack Compose, shared modules and academy-specific build flavors.
- iOS apps: Swift + SwiftUI, shared modules and academy-specific app targets.
- Backend A: Java 25 + Spring Boot.
- Backend B: PHP 8.5 + Laravel 13; equivalent API and business functionality.
- One React + TypeScript admin portal for all academies.

## Backend deployment principle
Spring Boot and Laravel are interchangeable implementations of one versioned API contract. Initially deploy only one backend to production to control operating cost and avoid dual-write/data-consistency problems. Keep both implementations aligned and test them against the same OpenAPI contract.

## Core principles
1. User-owned learning material is the primary source of truth.
2. Every lesson follows a consistent, interview-oriented structure.
3. Default answers are concise and speakable; expanded explanations and code are available on demand.
4. Content updates are independent of mobile app releases.
5. Existing repositories are reference-only and must not be modified by this project.
6. External material must be attributed and reviewed before publication.
7. Each app is academy-scoped; shared backend access does not mean cross-academy content leakage.

## JIA learning tracks
See [JIA Java Curriculum](docs/JIA_JAVA_CURRICULUM.md) for comprehensive coverage and [Multi-Academy Platform Strategy](docs/MULTI_ACADEMY_PLATFORM.md) for the shared-app/backend/admin architecture.

## External service setup

Follow the [External Configuration Setup Guide](docs/EXTERNAL_CONFIGURATION_SETUP.md) for Google Sign-In, Sign in with Apple and future SMS OTP provider configuration. Keep all credentials in environment secrets; never commit them.

## Repository layout
```
android/          Shared Android modules and academy app flavors
ios/              Shared iOS modules and academy app targets
backend/          Spring Boot API
backend-laravel/  Laravel API
admin-web/        Central admin portal
content/          Versioned learning content and seed data
docs/             Product, architecture, API and content specifications
infra/            Local development and deployment assets
```

## Project status
Catalogue, identity and early academy/content-governance database foundations are committed. Full CMS APIs/UI, assessment engine and learner apps remain under implementation. No lesson bodies are invented or published; user-provided material remains the priority.
