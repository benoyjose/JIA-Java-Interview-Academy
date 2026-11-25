# M3 — Spring Boot / Laravel parity matrix (#226)

**Deployment policy:** Spring Boot is the sole intended production writer. Laravel is a secondary compatibility implementation; do not run both as concurrent writers. Update this matrix in every feature PR that changes a shared API.

| Capability | Canonical contract | Spring Boot | Laravel | Client / parity evidence | Status |
|---|---|---|---|---|---|
| Identity, auth/session | docs/api/openapi.yaml; issue #6 | Primary; verify rotation/revocation/rate limits | Existing auth/provider/OTP paths; parity audit required | Cross-backend security contract tests | Gap |
| Academy context/tenant isolation | Issue #221; OpenAPI | Must derive scope from registered app identity | Validate equivalent middleware/policies | Tampering and cross-tenant negative tests | Gap |
| Catalogue/content | OpenAPI; #217 | Primary endpoint behavior | Compatibility mapping required | Response, pagination, publication visibility | Audit |
| Assessment/practice | #218 | Primary lifecycle | Compatibility mapping required | Scoring, attempt state, error semantics | Audit |
| Progress event ingestion | #189/#244 | Single-event/idempotency work exists; batch/projection completion outstanding | Receipt/schema compatibility | Shared event fixtures; retry/conflict cases | In progress |
| Offline content/versioning | #219 | Manifest/version contract | No independent writer; shared read semantics | Manifest/checksum/error fixtures | In progress |
| Entitlements/billing | #220 | Server-authoritative entitlement | Compatibility/read semantics only unless designated | Replay, refund, expiry and academy isolation | Gap |
| Learner export/deletion | #195, PR #353 contract | Implement and test contract | Match request/state/error semantics | Ownership, redaction, deletion retry | Contract defined |
| Reminders/goals | #222 | Scheduling/preferences parity | Quiet-hours slice; delivery lifecycle pending | Timezone/DST, opt-out, token lifecycle | In progress |
| Admin content governance | #187/#190/#196 | Publication/revision authority | Match shared workflow contract | Role, stale approval, audit and preview | Audit |

## Parity gate for a shared feature
- [ ] OpenAPI operation, DTO, validation, status codes and error envelope updated.
- [ ] Spring and Laravel behavior declared: parity required, Java-only, or intentionally deferred with issue link.
- [ ] Shared fixture tests cover success, validation, unauthorized, forbidden, not-found and pagination/state transitions.
- [ ] Ownership and academy isolation tested at API and persistence boundaries.
- [ ] Migration/schema assumptions documented; no competing production writes.
- [ ] Client contract and compatibility impact documented.
- [ ] CI result linked in the PR; matrix row updated with evidence.

Do not label a row complete based on isolated unit tests alone. This matrix is a living release gate, not a substitute for executable contract tests.
