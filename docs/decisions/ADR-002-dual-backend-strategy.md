# ADR-002: Two interchangeable backend implementations

- Status: Accepted
- Date: 2026-11-19

## Context

JIA needs a Java/Spring Boot backend and a PHP backend option to evaluate initial operating and development costs. Android and iOS clients must not become dependent on either backend framework.

## Decision

Maintain two implementations of the same backend API:
- Spring Boot (Java 21)
- Laravel 13 (PHP 8.3+)

The OpenAPI document is the normative HTTP contract. Both implementations must provide equivalent response schemas, validation, authorization, pagination, error codes and business behavior.

Only one backend is the active production writer at a time during the initial phase. The alternate backend is built and tested against the same contract in CI and may be deployed to isolated staging. Do not dual-write to the same production database.

Use one canonical PostgreSQL data model. Keep migrations semantically equivalent and review schema changes across both implementations.

## Rationale

- Keeps Android, iOS and admin clients independent of backend language.
- Allows a cost/performance/operability comparison.
- Avoids paying for two production deployments before product-market validation.
- Avoids split-brain writes and data drift.

## Consequences

- Every API change requires contract tests for both implementations.
- Domain rules must be expressed consistently and must not rely on framework-specific defaults.
- Database migrations need cross-backend review.
- Selecting Laravel for initial production does not mean removing Spring Boot; selecting Spring Boot does not mean removing Laravel.
- A later migration between implementations requires backup, compatibility checks and a controlled cutover.
