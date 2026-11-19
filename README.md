# JIA — Java Interview Academy

JIA is a learning and interview-readiness platform built around structured technical content, concise interview answers, hands-on code, and topic-wise assessments.

## Product surfaces

- **Android app:** Kotlin, Jetpack Compose, offline-first learning and practice.
- **Backend API:** Java 21, Spring Boot, PostgreSQL, Spring Security.
- **Admin portal:** React, TypeScript; manage lessons, questions, quizzes, publishing and app releases.

## Core principles

1. User-owned learning material is the primary source of truth.
2. Every lesson follows a consistent, interview-oriented structure.
3. Default answers are concise and speakable; expanded explanations and code are available on demand.
4. Content updates are independent of Android APK releases.
5. Existing repositories are reference-only and must not be modified by this project.
6. External material must be attributed and reviewed before publication.

## Learning tracks

Core Java; Advanced Java; Spring Framework; Spring Boot; Hibernate/JPA; Microservices; Messaging (Kafka and RabbitMQ); Databases; System Design; DevOps; Cloud and Observability; Engineering Excellence.

## Repository layout

```
android/       Android application
backend/       Spring Boot API
admin-web/     Admin portal
content/       Versioned learning content and seed data
docs/          Product, architecture, API and content specifications
infra/         Local development and deployment assets
```

## Project status

Initial foundation and specifications. Implementation will proceed in vertical slices, beginning with the content model and backend foundation.

See [Product Scope](docs/PRODUCT_SCOPE.md), [Content Model](docs/CONTENT_MODEL.md), and [Architecture](docs/ARCHITECTURE.md).
