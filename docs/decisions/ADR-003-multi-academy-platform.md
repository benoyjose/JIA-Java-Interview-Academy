# ADR-003: Multi-academy product architecture

- Status: Accepted
- Date: 2026-11-19

## Context
JIA (Java Interview Academy) launches first. The product may later expand to PIA (Python Interview Academy) and GIA (Go Interview Academy). Separate codebases would duplicate identity, assessments, progress, publishing, administration and mobile capabilities.

## Decision
Build one configurable academy platform. Academy-specific curriculum/content is isolated by academy identity; platform capabilities are shared. JIA is the only active academy at launch. Reserve PIA and GIA codes without creating public empty catalogues or invented content.

## Consequences
- One global user identity; enrollment, entitlements and progress are academy-specific.
- All academy-owned data is academy-scoped and protected by constraints and authorization.
- Editors/reviewers can be assigned per academy.
- Mobile and admin clients share components and API contracts; branding is configuration-driven.
- Existing JIA /technologies endpoints remain compatible during gradual migration to academy-explicit routes.
- Backend remains a modular monolith. Spring Boot and Laravel implement the same contract; only one is production-active at a time.
- JIA launch scope remains the priority.

## Rejected alternatives
1. Separate repos/apps/backends per academy: duplicates platform capabilities and raises cost.
2. One shared curriculum taxonomy: mixes language-specific concepts and weakens content quality.
3. Microservices at launch: unnecessary operational complexity before scale requires it.
