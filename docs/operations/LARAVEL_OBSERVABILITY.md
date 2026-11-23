## Laravel API observability

- Request IDs accept 1–64 safe ASCII characters; invalid values are replaced with UUIDs.
- Structured completion logs include correlation ID, method, route template, status and elapsed milliseconds.
- Raw URLs/query strings, request bodies, email addresses, access tokens and exception messages are excluded.
- `/api/v1/health/live` is public liveness and does not contact dependencies.
- `/api/v1/health/ready` performs a minimal database query and returns `503 NOT_READY` on failure without exposing diagnostics.
- Configure production JSON logs to stderr and collect centrally. This change does not claim to expose a Prometheus scrape endpoint.
