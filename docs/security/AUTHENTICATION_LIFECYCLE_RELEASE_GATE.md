# Authentication lifecycle production gate — issue #6

This is a cross-client release checklist, not a production-readiness certification.

- [ ] Access tokens are short-lived; refresh tokens rotate and are stored hashed server-side.
- [ ] Reuse of a rotated/revoked refresh token revokes its token family and emits an audit event.
- [ ] Account linking/unlinking requires fresh proof of control for both identities and has conflict/replay tests.
- [ ] Password reset is one-time, expiry-bounded, enumeration-resistant and revokes active access/refresh sessions.
- [ ] OTP provider delivery is tested end-to-end; production phone login remains disabled until provider configuration is verified.
- [ ] Per-IP, per-account and resend/verification throttles are consistent across Spring Boot and Laravel; proxy headers are trusted only from configured proxies.
- [ ] Google/Apple/SMS secrets come from deployment secret storage and never enter source or logs.
- [ ] iOS Google and Sign in with Apple SDK flows, session restore and logout are tested in an actual app target.
- [ ] Security regression tests cover expired/replayed tokens, linking conflicts, OTP replay, enumeration and rate-limit bypass.

The repository already contains Spring database-backed login throttling, password-reset logic and refresh-token persistence. Those implementations still require explicit integration/deployment evidence before this issue can close.
