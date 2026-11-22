# Branded app identity and release management

## Registry and API contract

The shared `academy_apps` registry maps a public `app_code` to exactly one academy, platform and application identifier. App codes route public configuration; they are not credentials, signing proof or authorization. The API checks the submitted platform and package/bundle identifier against the registered row and does not accept a client-provided academy ID.

- `GET /api/v1/apps/{appCode}/release-policy?platform=ANDROID&applicationId=com.example.app&installedVersion=1.0.0`
- `PUT /api/v1/apps/{appCode}/release-policy` (authenticated principal with `releases.manage`)

Policy responses provide minimum/current versions, optional HTTPS store URL, release notes and one of `CURRENT`, `UPDATE_AVAILABLE`, `UPDATE_REQUIRED`. Unknown/mismatched identities return 404; suspended or retired apps return 410. A planned app can retrieve its metadata but is not represented as a released store binary.

JIA Android is registered as `JIA_ANDROID`, `com.jia.academy.app`, version `0.1.0`, status `PLANNED`. No iOS bundle ID or PIA/GIA app is fabricated; register those only after the final identifiers and release owners are confirmed.

## Android binary configuration

The JIA application can build using release-specific Gradle properties. Defaults preserve the current local/CI identity:

```properties
JIA_APPLICATION_ID=com.jia.academy.app
JIA_APP_CODE=JIA_ANDROID
JIA_DISPLAY_NAME=JIA - Java Interview Academy
JIA_VERSION_CODE=1
JIA_VERSION_NAME=0.1.0
JIA_API_BASE_URL=https://api.example.invalid
JIA_GOOGLE_WEB_CLIENT_ID=<configured OAuth web client ID>
```

Pass these through a protected release environment or local `~/.gradle/gradle.properties`; do not commit production OAuth client secrets, keystores, signing passwords or service-account credentials. Public OAuth client IDs are identifiers rather than private keys, but they still belong in environment-specific configuration and must match the registered Android package/signing certificate.

Each branded app/store listing has its own application ID, app code, display name, version code/name, icon, privacy disclosures, signing identity and distribution workflow. Content publication remains separate from binary versioning.

## Store signing and release pipeline

1. Confirm package/bundle ID, app code, academy association and store owner before changing registry state to `ACTIVE`.
2. Store signing material in GitHub Actions environment secrets or a managed signing service with restricted access; never check it into source control or logs.
3. Use independent protected release environments and approval gates per app/platform. Build and sign only the target app; publish to its matching store listing.
4. Set minimum/current supported versions and HTTPS store URL through the protected release-policy operation. Suspend/retire a compromised or unsupported app without affecting other academy apps.
5. Keep signing key backups and rotation/recovery procedures with the store owner. Package/bundle identifiers are not proof of app authenticity; use platform attestation only when threat modeling justifies it.

The shared repository currently has a JIA Android binary configuration. Separate iOS targets and store automation remain contingent on finalized bundle ID, signing and release ownership.
