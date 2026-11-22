# Academy subscriptions and entitlement lifecycle

Subscriptions, plans and entitlements are scoped to the learner and academy. Premium access is determined by server-side entitlement records; client purchase flags are never authorization inputs.

## Lifecycle
- Plans belong to an academy and define a code, price in minor currency units, ISO currency, billing interval and feature metadata. Archive plans used by subscriptions rather than deleting them.
- A subscription links a user, academy, plan and provider subscription reference. Provider references are unique, and the composite FK prevents cross-academy plan association.
- Entitlements are keyed by user + academy + code. ACTIVE and GRACE are valid only until their validity timestamp; expired or revoked records do not grant access.
- Provider events are unique by provider + event ID. Replayed events are acknowledged as DUPLICATE and do not repeat the state transition.

## Provider boundary
POST /api/v1/payments/webhooks/{providerCode}
Header: X-Payment-Signature: sha256=<hex HMAC-SHA256>

The HMAC is calculated over the exact raw request body using JIA_PAYMENT_WEBHOOK_SECRET. Store this secret in deployment environment/secret management, never source control or database records. If absent, webhook processing returns 503. A provider adapter should translate its verified payload into the normalized contract before forwarding. Do not disable signature validation.

Supported normalized events: subscription.activated, subscription.renewed, payment.succeeded, payment.failed, subscription.expired, subscription.cancelled and payment.refunded. Unknown types are recorded as IGNORED. Transient failures can be retried safely.

## Client contract
- Fetch GET /api/v1/me/academies/{academyCode}/entitlements after sign-in and after purchase, restore, renewal or cancellation.
- Client checkout/restore confirms with the provider, then refreshes server state. Never send an isPremium flag as proof.
- Published lessons without entitlement requirements remain public. A lesson with a row in lesson_entitlement_requirements is delivered only when the authenticated learner has every required, unexpired entitlement in that lesson's academy.
- Live provider checkout, platform receipt verification and cancellation UI belong in provider/platform adapters. This foundation intentionally hard-codes no payment vendor or price.
