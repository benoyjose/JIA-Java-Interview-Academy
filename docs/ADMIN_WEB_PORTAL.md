# JIA Admin Web Portal

## Purpose

The central React + TypeScript portal manages content across academy apps. The first JIA slice uses the existing Spring Boot admin API for lesson authoring, review and publication. Laravel remains secondary and is not used by this portal.

## Architecture

- Browser SPA: React 19 + TypeScript, bundled by Vite.
- API: Spring Boot `/api/v1`, same-origin in production.
- Authentication: opaque bearer token from `POST /auth/login`; `GET /auth/me` validates the session; `POST /auth/logout` revokes it.
- Authorization: Spring Security permissions are authoritative. UI presentation never replaces API authorization.
- No local content cache or IndexedDB in the first release.

## Performance

- Initial JavaScript bundle CI guard: 350 KiB raw total; Vite warns on individual chunks above 250 KiB. Revisit with gzip/Brotli measurements before launch.
- Lazy-load lesson and review screens; avoid a heavyweight rich-text editor until the block schema is finalized.
- Use server-side prefix search, status filtering and deterministic pagination for both lesson and review lists. Page sizes are bounded to 1–100 (25 by default); never fetch the full catalogue or review queue. PostgreSQL B-tree pattern indexes support prefix filtering and composite/partial indexes support stable list ordering.
- Fetch full lesson/review revisions only on selection. Abort stale detail requests.
- No background polling; refresh after user actions.
- Validate JSON on save/submit, not on every keystroke.
- Render lesson JSON as escaped text, never untrusted HTML.

## Security

- Keep opaque access token in tab-scoped `sessionStorage`, never `localStorage`.
- HTTPS and same-origin API routing in production; configure restrictive CSP and avoid untrusted scripts on the admin origin.
- All `VITE_*` values are public browser configuration, never secrets.
- Treat API 401/403 responses as authoritative.

## Current scope

- Sign in/out.
- Lesson list, status filter and bounded search.
- Create/update draft lessons, including separate draft revisions for published lessons.
- Submit drafts for review.
- Inspect exact pending revision snapshots; approve/request changes/reject.
- Publish approved lessons.

## Follow-ups

1. Measure query plans and consider keyset pagination if offset pagination becomes expensive at very large catalogue sizes.
2. Add a schema-aware block editor after content block schema is finalized.
3. Add browser E2E tests against disposable Spring Boot/PostgreSQL.
4. Add deployment CSP/security headers and production gzip/Brotli reporting.
5. Add academy selector and academy-scoped content after shared academy APIs are implemented.