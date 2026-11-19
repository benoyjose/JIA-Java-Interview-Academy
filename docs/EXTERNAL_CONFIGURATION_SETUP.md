# JIA External Configuration Setup

A practical checklist for configuring Google Sign-In, Sign in with Apple and SMS OTP services. Complete this document when setting up a local, test or production environment.

> Keep this document separate from application code. Never commit provider secrets, private keys, SMS credentials or production environment files to GitHub.

## 1. Before you start

You need:

- Access to the JIA Google Cloud project (or permission to create one).
- An Apple Developer account with access to Certificates, Identifiers & Profiles.
- A registered JIA Android application and iOS application with final package/bundle identifiers.
- An SMS provider account before enabling phone login. The provider has not yet been selected.
- Separate credentials for development, staging and production.

Write down these app identifiers before configuring providers:

| Item | Value |
|---|---|
| Android application ID | `<your.android.application.id>` |
| iOS bundle ID | `<your.ios.bundle.id>` |
| Apple Services ID (if using a web/service flow) | `<your.apple.services.id>` |
| Backend public URL | `https://<your-api-domain>` |

Do not use example values in a real deployment.

## 2. Google Sign-In

### A. Create or select a Google Cloud project

1. Open [Google Cloud Console](https://console.cloud.google.com/).
2. Create a project named **JIA** or select the existing JIA project.
3. Open **Google Auth Platform** (or the OAuth consent screen setup).
4. Configure the app name, support email and developer contact email.
5. Choose the appropriate audience for your rollout. Keep the app in testing while validating it; add tester accounts as required.
6. Complete the verification steps Google requires before public release.

### B. Create OAuth client IDs

1. In Google Auth Platform, open **Clients**.
2. Create an Android OAuth client using the exact Android application ID and signing certificate SHA-1 fingerprint.
3. Create an iOS OAuth client using the exact iOS bundle ID.
4. Create a Web application OAuth client for backend ID-token audience validation.
5. Record the client IDs. A client ID is not a client secret; do not embed a web client secret in either mobile app.
6. For Android, register both debug and release signing fingerprints if both builds need sign-in. Register the Play App Signing certificate fingerprint for Play-distributed builds.

### C. Configure JIA

Set the backend audience to the **Web application client ID** used by the mobile Google Sign-In flow:

```env
GOOGLE_OAUTH_CLIENT_ID=<google-web-client-id>
```

Configure the Android and iOS applications with their respective platform client IDs using the official Google identity SDKs. The mobile applications must request an ID token for the configured server/web client ID so the backend audience check matches.

### D. Test

- Test with an approved tester account.
- Confirm the backend accepts a valid ID token.
- Confirm an invalid, expired or wrong-audience token is rejected.
- Confirm the same Google account resolves to the same JIA user on Android and iOS.

Official starting point: [Google Identity documentation](https://developers.google.com/identity).

## 3. Sign in with Apple (iOS)

### A. Enable the capability

1. Sign in to [Apple Developer](https://developer.apple.com/account/).
2. Open **Certificates, Identifiers & Profiles**.
3. Select **Identifiers** and open the JIA iOS App ID.
4. Enable **Sign in with Apple** and save the change.
5. In Xcode, enable the **Sign in with Apple** capability for the iOS target and confirm it uses the matching team and bundle identifier.

### B. Configure the backend audience

1. Identify the exact client identifier sent as the `aud` claim by the iOS Sign in with Apple flow. For a native app this is commonly the app's bundle identifier; a Services ID is used for a web/service flow.
2. Set the backend value to that exact identifier:

```env
APPLE_SERVICE_ID=<exact-apple-token-audience>
```

The current backend configuration accepts one Apple audience value. If the app later uses multiple Apple audiences (for example, native and web), update the backend to allow an explicit configured list before enabling both.

### C. Important Apple details

- Use Apple's stable `sub` claim as the identity key, not the email address.
- Users may choose Apple's private relay email. Treat it as a valid address.
- Apple may provide the user's name only during the first authorization. Capture it securely in the app and pass it only as optional profile information.
- Generate and validate a fresh nonce for each sign-in. The backend currently expects the SHA-256 hash of the nonce in the Apple token.
- Never put an Apple private key or client secret in the iOS app.

Official starting point: [Sign in with Apple](https://developer.apple.com/sign-in-with-apple/).

## 4. Phone number login (SMS OTP)

**Do not enable phone login yet.** JIA's SMS vendor has not been selected and the OTP send/verify endpoints are not yet implemented.

When selecting a provider, evaluate:

- Delivery reliability for India and the other launch countries.
- DLT/template and sender-ID requirements for India.
- International coverage, delivery reports and retry behavior.
- Per-message pricing, taxes and monthly minimums.
- API security, data residency, retention and support.
- Spend limits, fraud controls and rate limiting.

After a provider is selected, obtain its API credentials and sender/template configuration. Store them only as server-side secrets. The integration must enforce E.164 phone normalization, short OTP expiry, single use, resend cooldown, attempt limits and per-phone/IP/device throttling. Store only a keyed digest of the OTP; never log the code.

Add the final provider-specific environment variable names and setup instructions here after the SMS vendor is approved. Do not invent credentials or configure a fake OTP delivery path.

## 5. Backend environment variables

For both Spring Boot and Laravel backends, configure these values in the deployment secret manager or runtime environment:

```env
GOOGLE_OAUTH_CLIENT_ID=<google-web-client-id>
APPLE_SERVICE_ID=<exact-apple-token-audience>
```

The backend has safe defaults for the public Google and Apple JWKS endpoints and issuer values. Override those only when using an approved provider configuration.

For local development, use an untracked environment file or your IDE's environment-variable configuration. For staging/production, use the hosting platform's secret manager. Never add a real `.env` file to Git.

Laravel uses the same `GOOGLE_OAUTH_CLIENT_ID` and `APPLE_SERVICE_ID` values. Its token verification endpoints are implemented, but do not switch production traffic to Laravel until dependency resolution, provider verification and shared contract tests pass.

## 6. Environment checklist

| Check | Local | Staging | Production |
|---|---:|---:|---:|
| Google Android client configured | ☐ | ☐ | ☐ |
| Google iOS client configured | ☐ | ☐ | ☐ |
| Google Web/server client ID configured | ☐ | ☐ | ☐ |
| Correct Android signing fingerprints registered | ☐ | ☐ | ☐ |
| Apple Sign in with Apple capability enabled | ☐ | ☐ | ☐ |
| Apple token audience matches backend config | ☐ | ☐ | ☐ |
| Provider secrets stored outside Git | ☐ | ☐ | ☐ |
| Valid-token and invalid-token tests passed | ☐ | ☐ | ☐ |
| Cross-platform account consistency tested | ☐ | ☐ | ☐ |
| SMS provider selected and security controls tested | ☐ | ☐ | ☐ |

## 7. Troubleshooting quick checks

- **Google audience error:** the ID token's `aud` must equal `GOOGLE_OAUTH_CLIENT_ID`. Make sure the mobile SDK requests the server/web client ID.
- **Google Android sign-in fails only in release:** check the release or Play App Signing SHA-1 fingerprint in the Android OAuth client.
- **Apple audience error:** compare the token's `aud` with `APPLE_SERVICE_ID`; it must be the exact client identifier used by the sign-in flow.
- **Apple nonce error:** ensure the app creates a new nonce for each attempt and sends the original nonce to the backend; the backend compares its SHA-256 hash with the token claim.
- **Account conflict:** do not merge users because their email addresses match. Sign in with the existing method, then implement the authenticated account-linking flow.
- **Provider login unavailable:** check that the corresponding client ID is configured in the backend environment and that outbound HTTPS access to the provider's JWKS endpoint is allowed.

## 8. Current implementation boundary

At the time this guide was written:

- Spring Boot and Laravel have Google and Apple ID-token verification endpoints and provider-neutral identity persistence.
- Android and iOS provider SDK integrations are still pending.
- Phone OTP send/verify endpoints and the SMS vendor integration are still pending.
- Email/password login is implemented in both backend foundations.

External configuration alone does not make a sign-in method production-ready. Enable each method only after its app integration, backend implementation, security review and end-to-end tests are complete.
