# Learner data export API

`GET /api/v1/me/privacy/export` is an authenticated Spring API endpoint. It exports only rows owned by the authenticated user: profile fields, lesson progress, bookmarks, quiz attempts and learner responses, revision schedule, notification preferences and in-app notifications.

The JSON includes `formatVersion` and `generatedAt`. Password hashes, access/refresh tokens, reset tokens, provider identity secrets, server-side question answer keys, other learners' data and operational mail payloads are excluded.

This endpoint is the export slice of privacy issue #195. Account deletion, explicit confirmation, revocation and Laravel parity are separate acceptance items and are not claimed complete by this change.
