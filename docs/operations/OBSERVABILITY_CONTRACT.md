# Observability Contract

This document defines the minimum production observability contract for the JIA Java Interview Academy APIs and clients. It is a design and implementation baseline; it does not claim that every item is currently instrumented.

## Correlation and structured logs

- Accept or generate a request correlation ID at each API boundary; return it in the response header.
- Propagate the ID through application logs and downstream calls where supported.
- Emit structured JSON logs in production, with stable field names: timestamp, severity, service, environment, request_id, route template, HTTP method, status, duration_ms, and error_code.
- Never log access tokens, passwords, session cookies, raw request bodies, answer submissions, or learner personal data. Prefer route templates over raw URLs to avoid identifiers in paths.
- Keep exception stack traces in server logs; return a generic error response plus request ID to clients.

## Metrics contract

Use bounded-cardinality labels only. Do not label metrics with user IDs, lesson slugs, request IDs, raw URLs, or exception messages.

| Metric | Type | Required labels | Purpose |
|---|---|---|---|
| `http_server_requests_total` | Counter | service, method, route, status | Request volume and error rate |
| `http_server_request_duration_seconds` | Histogram | service, method, route | Latency percentiles |
| `dependency_requests_total` | Counter | service, dependency, outcome | Dependency failure rate |
| `dependency_request_duration_seconds` | Histogram | service, dependency | Dependency latency |
| `background_jobs_total` | Counter | service, job, outcome | Worker reliability |
| `background_job_duration_seconds` | Histogram | service, job | Worker duration |

Use the platform-native metric implementation (Micrometer/Prometheus for Spring Boot; an equivalent Laravel-compatible exporter) while preserving semantic names and label meaning. Export only operational metrics, not learner activity or quiz content.

## Health endpoints

- **Liveness**: process is alive; must not depend on remote systems.
- **Readiness**: instance can serve requests; include critical local dependencies only.
- **Dependency health**: database and required integrations are reported separately, with timeouts.
- Protect detailed diagnostic endpoints from public exposure; public responses should not reveal hostnames, credentials, or infrastructure topology.

## Initial dashboard panels

1. Request rate, 4xx/5xx rate, and p50/p95/p99 latency by service and route template.
2. Database connection pool saturation, query latency, and connection failures.
3. Dependency error rate and latency.
4. Background queue depth, retry rate, and oldest queued item age.
5. Deployment version, instance health, and restart count.

## Alerting and response

- Alert on sustained elevated 5xx rate, latency SLO breach, unavailable readiness, database exhaustion, and persistent queue backlog.
- Use a sustained window and a minimum traffic threshold to avoid noisy alerts.
- Every alert must link to a runbook with diagnosis, mitigation, escalation, and recovery verification.
- Do not alert on individual learner actions or expose learner identifiers in alert payloads.

## Cross-backend parity and validation

Spring Boot and Laravel implementations must expose equivalent health semantics, correlation behavior, metric definitions, and error-response correlation fields. Add automated tests for request ID propagation, sensitive-field redaction, health response shape, and bounded metric labels before marking the operational workstream complete.

## Delivery status

This contract is the first deliverable for issue #194. Instrumentation, dashboards, alert rules, and runbooks remain implementation tasks and must be verified independently.
