# JIA — Java Interview Academy

JIA is a learning and interview-readiness platform built around structured technical content, concise interview answers, hands-on code, and topic-wise assessments.

## Product surfaces

- **Android app:** Kotlin + Jetpack Compose; offline-first learning and practice.
- **iOS app:** Swift + SwiftUI; feature parity with the learner experience on Android.
- **Backend A:** Java 21 + Spring Boot.
- **Backend B:** PHP 8.3+ + Laravel 13; equivalent API and business functionality.
- **Admin portal:** React + TypeScript; content, assessment, publishing and release management.

## Backend deployment principle

Spring Boot and Laravel are interchangeable implementations of one versioned API contract. Initially deploy only one backend to production to control operating cost and avoid dual-write/data-consistency problems. Keep both implementations aligned and test them against the same OpenAPI contract. Laravel can be evaluated as the lower-cost initial deployment; Spring Boot remains the alternative implementation.

## Core principles

1. User-owned learning material is the primary source of truth.
2. Every lesson follows a consistent, interview-oriented structure.
3. Default answers are concise and speakable; expanded explanations and code are available on demand.
4. Content updates are independent of mobile app releases.
5. Existing repositories are reference-only and must not be modified by this project.
6. External material must be attributed and reviewed before publication.
7. Android and iOS should share product behavior and API contracts, with platform-native UX.

## Learning tracks

Core Java; Advanced Java; Spring Framework; Spring Boot; Hibernate/JPA; Microservices; Messaging (Kafka and RabbitMQ); Databases; System Design; DevOps; Cloud and Observability; Engineering Excellence.

## Repository layout

```
android/          Android application
ios/              iOS application
backend/          Spring Boot API
backend-laravel/  Laravel API
admin-web/        Admin portal
content/          Versioned learning content and seed data
docs/             Product, architecture, API and content specifications
infra/            Local development and deployment assets
```

## Project status

Spring Boot catalogue foundation is committed. Cross-platform/backend architecture and API parity contract are being established. No lesson bodies are invented or published; user-provided material remains the priority.

See [Product Scope](docs/PRODUCT_SCOPE.md), [Content Model](docs/CONTENT_MODEL.md), [Architecture](docs/ARCHITECTURE.md), [Backend Parity Decision](docs/decisions/ADR-002-dual-backend-strategy.md), and [OpenAPI Contract](docs/api/openapi.yaml).
