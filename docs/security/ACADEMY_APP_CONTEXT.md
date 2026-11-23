# Registered academy-app request context

The registered app identity is resolved on the server from the three headers:

- `X-Academy-App`: registry app code (for example, the already-registered JIA code)
- `X-Academy-Platform`: platform enum accepted by the registry
- `X-Academy-Application-Id`: exact package/bundle identifier registered for that app

Send all three together. The backend looks up the active app and its academy mapping; clients must not treat `X-Academy-Id` as an authority. If that optional compatibility header is present, it must match the server-resolved academy UUID.

## Enforcement in this slice

- Every `/api/v1/admin/**` request, including GET/read endpoints, is rejected with HTTP 400 when registered app identity is absent or incomplete.
- Unknown registrations are rejected; suspended/retired apps and inactive academies are rejected by the resolver.
- A supplied academy UUID that differs from the registry mapping is rejected with HTTP 403.
- Successful resolution is attached to the request as `AcademyAppContextInterceptor.REQUEST_CONTEXT_ATTRIBUTE`; downstream admin authorization/data access must use this server-resolved context.

Non-admin headerless routes remain in compatibility mode to avoid breaking existing public and learner clients while they migrate. This change is a guardrail slice for issue #221, not a claim that all controllers/repositories have completed academy-scoped authorization. Follow-on work must bind each admin query/mutation to the resolved academy and enforce academy-scoped editor/reviewer grants, including repository and database constraints. Do not activate planned app registrations or invent IDs.
