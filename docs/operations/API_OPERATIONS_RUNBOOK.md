# Operations runbook — JIA API

This runbook complements `OBSERVABILITY_CONTRACT.md`. It is an operator checklist; configure dashboards and alert thresholds against measured staging/production baselines rather than treating example thresholds as universal.

## Endpoint verification
- `GET /actuator/health/liveness`: process liveness only; restart only when this fails persistently.
- `GET /actuator/health/readiness`: local serving readiness. Remove an unready instance from traffic; inspect database pool and migration status before restart.
- `GET /actuator/prometheus`: scrape from a private monitoring network only. Do not publish this endpoint to the internet or expose it through learner/admin ingress.
- Public health responses must not include dependency details, hostnames, credentials or topology.

## First-response checklist
1. Confirm deployment version, environment and start time.
2. Compare request rate and 5xx/latency with the previous stable window.
3. Check readiness, DB connection pool saturation, connection errors and slow query trend.
4. Check queue depth/retries/oldest job where workers are enabled.
5. Correlate logs using `X-Request-Id`; search only route template and status, never user IDs or raw payloads.
6. If the issue began with a release, stop rollout and roll back to the last verified image/tag. Preserve logs and migration state.
7. Verify recovery with health, a safe authenticated smoke test and stable error/latency trend.

## Initial alert policy (tune after baseline)
- API 5xx ratio > 5% for 10 minutes, with minimum traffic.
- p95 route latency above the route's agreed SLO for 10 minutes.
- Readiness failure for 3 consecutive probes.
- Database pool > 85% utilization for 10 minutes or repeated connection acquisition failures.
- Queue oldest item age above its service-specific processing objective.

Each alert owner must add escalation contacts, dashboard link and service-specific recovery commands before production enablement.

## Metric cardinality and privacy
Use route templates, not request paths; status class or bounded status; dependency/job names from fixed allowlists. Never use request IDs, user/academy identifiers, lesson/question slugs, email, raw exception text or arbitrary client labels.

## Backend parity
Laravel must retain the same request ID header/log correlation contract and publish operational metrics with equivalent semantic labels. Do not claim Java and Laravel metric parity until Laravel exporter and endpoint tests are deployed and verified.
