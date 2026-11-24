# Store billing integration gate — issue #220

## Security invariant
Premium access is granted only from a server-verified provider transaction and the academy-scoped entitlement API. A client purchase flag, local receipt string, UI state or unverified callback must never authorize premium content.

## Provider work required before activation
- Android: Google Play Billing purchase and acknowledgement, pending state, restore/query purchases, subscription management deep link and sandbox verification.
- iOS: StoreKit purchase/restore and App Store server notification/receipt verification, with sandbox lifecycle tests.
- Backend: provider adapter validates signed transaction/token using provider APIs; bind transaction and subscription IDs to authenticated user + registered academy; persist idempotency keys; process renewal, grace, cancellation, refund and revocation.
- Operations: protected credentials, webhook signature/replay tests, reconciliation, audit events, provider-outage behavior and support runbook.

## Release blockers
Provider/platform policy and pricing terms must be approved before checkout is enabled. Existing entitlement/webhook code is not evidence of completed native purchase, restore or provider verification. Public catalogue remains browse-first; failed verification fails closed for premium access.
