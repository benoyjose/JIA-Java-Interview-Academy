# JIA Laravel API

Laravel implementation of the JIA backend contract. This application is intended to provide the same learner/admin functionality and JSON responses as the Spring Boot backend.

## Requirements

- PHP 8.3+
- Composer 2
- PostgreSQL 16+
- Laravel 13

## Setup

1. Install dependencies: `composer install`.
2. Copy `.env.example` to `.env` and configure the PostgreSQL connection.
3. Generate an application key: `php artisan key:generate`.
4. Point the application to a database initialized with the canonical JIA schema.
5. Start locally: `php artisan serve`.

The initial Laravel slice reads the existing shared catalogue tables. It intentionally does not introduce a second divergent schema or invent lesson content. The Spring Boot Flyway migration is currently the canonical initial schema source; a Laravel migration-equivalent and automated schema parity checks are still required before Laravel can be considered production-ready.

## Catalogue routes

- `GET /api/v1/technologies`
- `GET /api/v1/technologies/{slug}`
- `GET /api/v1/technologies/{technologySlug}/categories`
- `GET /api/v1/categories/{categorySlug}/lessons`
- `GET /api/v1/lessons/{slug}`

Only `PUBLISHED` records are returned. Response field names and JSON shapes are defined in `docs/api/openapi.yaml`.

## Current limitations

- This is an initial API scaffold, not a production-ready Laravel deployment.
- Authentication, authorization, admin authoring, review/publishing, assessments, progress, content synchronization, audit and release management are not implemented yet.
- No Composer install, PHP lint, PHPUnit, or database integration test has been run in this repository environment.
- Do not point both backends at the production database as simultaneous writers. See ADR-002.
