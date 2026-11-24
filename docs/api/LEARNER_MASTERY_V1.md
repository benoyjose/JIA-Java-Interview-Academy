# Learner mastery API v1

```http
GET /api/v1/me/mastery
Authorization: Bearer <learner access token>
Accept: application/json
```

The API returns a model version and category-level submitted-answer evidence for the authenticated learner. Clients derive an accuracy band only when answer count is positive; zero evidence must render as **Not assessed**, never 0% mastery. Completed lesson counts are contextual and do not independently certify mastery.

The endpoint intentionally takes no user identifier. Cross-learner lookup is not part of this contract. The API's academy scope is enforced server-side.
