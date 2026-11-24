# Open issue parallel delivery board — 2026-11-24

This board is a coordination snapshot, not an issue-closure report. Work is split by dependency so independent client, backend, content and release tasks can proceed concurrently. A merged PR is a completed slice; an issue closes only after its full acceptance criteria are evidenced.

## Active PRs / merged slices

| Issue | Work item | PR state |
|---|---|---|
| #221 | Remove fixed academy UUID from mastery summary; use registered app context and membership check | PR #333 merged |
| #244 | Propagate WorkManager cancellation without false failure bookkeeping | PR #334 open; Android unit-test dependency download failed (MavenArtifactFetcher IOException); instrumentation still running |
| #186 | Report duplicate curriculum prerequisite edges | PR #335 merged; sequencing/mastery acceptance remains |
| #159 | Advanced curriculum domain coverage register | PR #336 merged; domain lesson depth/review remains |
| #8 | Evidence-based iOS app-target/parity release gate | PR #337 merged; native Xcode app target and end-to-end parity remain |
| #220 | Fail-closed store verification and activation gate | PR #338 open; provider decision/sandbox implementation remain |
| #6 | Authentication lifecycle production evidence gate | PR #339 open; end-to-end security/provider work remains |
| #226 | Backend parity matrix and CI governance | PR #330 merged; behavioral contract tests remain |
| #187 | Editorial source-provenance publication validation | PR #331 merged; full checklist/reporting remains |

## Concurrent remaining workstreams

### Backend / security
- #6 authentication lifecycle, provider SDKs, refresh reuse and OTP delivery
- #221 end-to-end academy context and repository/admin isolation
- #226 behavioral Spring Boot/Laravel contract tests
- #195 privacy export, account deletion and retention policy
- #220 provider receipt verification, restore/revocation and entitlement reconciliation
- #189 transactional progress projections and history/sync contract
- #244 server/client reconciliation, partial failure and conflict recovery
- #11 cross-backend CI, staging smoke, backup/restore and rollback evidence

### Learner clients
- #219 offline release downloads, integrity, cache retention and version reconciliation (Android + iOS)
- #244 durable event outbox, exact receipt acknowledgements, restart and conflict recovery
- #222 reminder preference persistence, timezone/quiet-hours, token lifecycle and delivery adapters
- #196 WCAG audit, automated checks and critical screen remediation
- #8 native iOS app target, provider sign-in, UI tests and release pipeline

### Admin / curriculum / learning
- #190 authoring tree, editor validation, revision diff, preview and import usability
- #187 revision-bound review, publication gates, audit and editorial reports
- #186 versioned mastery, prerequisite graph and explainable adaptive plan
- #191 reviewed starter curriculum with validated examples and assessment rubrics
- #159 advanced lesson content across all domains; security and engineering leadership need explicit depth
- #197 isolated coding-execution threat model and mock-backed MVP; never run submissions inside the API process

## Merge and completion policy
- Keep PRs independent and rebased against current `main`; avoid conflicting shared migration edits.
- Run each affected client/backend test suite and contract tests.
- Merge only after all required checks pass; retry infrastructure failures only after the active workflow finishes.
- Update issue acceptance evidence and close only when the entire issue scope is verified.
