# JIA API

Spring Boot 4.1.x / Java 25 LTS backend for JIA.

## Local setup

1. Install JDK 25, Gradle 9.9.1 and Docker.
2. Start PostgreSQL from the repository root: `docker compose -f infra/docker-compose.yml up -d`.
3. From the repository root, run: `gradle -p backend bootRun`.
4. Check health at `http://localhost:8080/actuator/health`.

Flyway applies schema migrations automatically. Hibernate uses schema validation; it does not create or mutate tables.

## Learner catalogue endpoints

- `GET /api/v1/technologies`
- `GET /api/v1/technologies/{slug}`
- `GET /api/v1/technologies/{technologySlug}/categories`
- `GET /api/v1/categories/{categorySlug}/lessons`
- `GET /api/v1/lessons/{slug}`

Only records in `PUBLISHED` status are returned. The initial migration seeds taxonomy only; it intentionally does not invent or publish lesson bodies.

## Configuration

Override `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` and `SERVER_PORT` through environment variables. Local Compose credentials are for development only; never reuse them in a deployed environment.

## Current scope

This is the read-only catalogue foundation. Authentication, admin authoring, content review/publishing, assessments, learner progress and content synchronization remain subsequent implementation increments.
