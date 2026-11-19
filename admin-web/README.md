# JIA Admin Web

React + TypeScript administration portal for the JIA academy family. This first slice includes administrator sign-in, lesson listing and authoring, revision submission, review decisions and publishing through the Spring Boot API.

## Runtime

- Node.js 26.x
- React 19.3.1
- Vite 8.3.8
- TypeScript 7.0.2
- Vitest 5.1.1

Direct dependencies are pinned. Run `npm install` from this directory.

## Local development

1. Start Spring Boot on `http://localhost:8080`.
2. Copy `.env.example` to `.env.local` if you need to override the API target.
3. Run `npm install` and `npm run dev`.

Vite proxies `/api/*` to Spring Boot. Production should route the same-origin `/api/v1` path through the deployment reverse proxy.

## Authentication

The portal uses the existing email/password endpoint and opaque bearer token. The token is held in tab-scoped `sessionStorage`, not persistent `localStorage`, and revoked through logout. The API remains the authorization boundary. Use HTTPS and a restrictive Content Security Policy in production; do not host untrusted scripts on this origin.

## Performance

- Vite production build with route-level lazy loading for lesson and review screens.
- No UI component framework or large editor dependency in the initial bundle.
- Search uses `useDeferredValue` over the bounded lesson-list response (API currently returns at most 100 rows).
- Full lesson and review snapshots are fetched only after selection; stale detail requests are aborted.
- No periodic polling; lists refresh after explicit user actions and mutations.
- JSON validation runs on save/submit, not every keystroke.
- CSS content visibility for list rows, responsive layout and reduced-motion support.
- Build warns on chunks above 250 KB; CI enforces a 350 KiB total JavaScript raw-size guard.

## Content

Lesson content is edited as JSON. The portal validates syntax and the required `schemaVersion` and `blocks` shape before sending it; Spring performs authoritative validation.

## Scripts

- `npm run dev` — development server
- `npm run check` — TypeScript validation
- `npm test` — unit tests
- `npm run build` — type-check and production bundle