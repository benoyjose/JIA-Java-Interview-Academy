# ADR-003: Shared multi-academy platform with separately marketed apps

- Status: Accepted
- Date: 2026-11-19

## Context
JIA launches first. PIA and GIA may follow. Each academy is marketed and distributed as its own Android and iOS app, while all apps are operated from one backend platform and one central admin portal.

## Decision
Build one configurable academy platform and shared codebase, but publish distinct branded app identities per academy and platform. Each app is mapped to one academy by server-managed application registration. All apps use the shared API, identity system, database, storage, content services, assessment engine, subscriptions/entitlements and central admin portal.

JIA is the only active academy at launch. Reserve PIA and GIA codes without public empty catalogues or invented content.

## Consequences
- Each academy has separate Android/iOS store listings, names, icons, screenshots, package/bundle identifiers, store metadata and release tracks.
- Apps share code modules, design system, API client, authentication, sync and feature modules where practical.
- A public app code selects academy context; it is not an authentication credential. Server validates app registration and separately checks user authentication, enrollment and entitlements.
- One global user identity can access multiple academies, with independent progress, attempts, bookmarks and entitlements.
- One admin portal manages all academies. Editors/reviewers are academy-scoped; global admins can switch academy.
- Shared backend services and database are used. No per-academy backend deployment is required initially.
- Existing JIA endpoints remain compatible during gradual API migration.
- Spring Boot and Laravel implement the same contract; only one backend is production-active at a time.

## Rejected alternatives
1. One store app containing all academies: conflicts with separate marketing/store listings.
2. Separate backend/admin/database per academy: duplicates operations and raises cost.
3. Fully separate client codebases: duplicates fixes; use shared modules and branded build targets.
4. Microservices at launch: unnecessary operational complexity.
