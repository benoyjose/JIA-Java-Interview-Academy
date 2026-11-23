# Coding Practice Execution: Threat Model and MVP Contract

Issue #197. This document is the security gate for Java code-submission execution. **The application/API process must never compile or execute learner-submitted source code.**

## Architecture decision

Use an asynchronous control plane and isolated execution data plane:

1. Android/web client submits exercise ID, language/runtime profile, source, and an idempotency key.
2. Spring Boot authenticates the learner, checks academy/exercise publication and submission quotas, stores a submission record, and enqueues an opaque job ID.
3. A dedicated sandbox orchestrator leases a job and starts a fresh, disposable execution environment for that submission.
4. The environment compiles and runs against a bounded set of tests, emits a normalized result, and is destroyed.
5. The control plane stores sanitized results and exposes status polling. The learner never receives hidden test source, expected values, worker paths, or infrastructure diagnostics.

For the MVP, implement the API and UI against a deterministic mock executor. Real execution is feature-flagged off until an independent sandbox implementation passes the security checklist and penetration review.

## Threat model

| Threat | Required control |
|---|---|
| Submission escapes process/container | Per-job microVM or hardened sandbox boundary; no shared host process |
| Network exfiltration / SSRF | No outbound or inbound network in execution namespace; deny DNS |
| Host filesystem or secret access | Read-only minimal image; per-job scratch only; no host mounts, credentials, service tokens, or environment secrets |
| CPU/memory/process exhaustion | Hard CPU, memory, PID, file-size, stdout/stderr, compile and run deadlines; kill entire cgroup on timeout |
| Hidden-test disclosure | Private tests stay server-side; return aggregate verdict and limited public diagnostics only |
| Cross-submission contamination | Fresh ephemeral environment per job; no reusable writable state |
| Abuse / cost attack | Authenticated user, per-user/academy concurrency and daily limits, queue caps, payload limits, cancellation and audit events |
| Malicious result/log injection | Structured output schema; escape source/logs; redact paths and infrastructure details |
| Replay / duplicate submission | Client idempotency key scoped to learner and exercise; stable submission ID |
| Unauthorized status/result access | Ownership and academy scope enforced on every read and cancel operation |

## Execution profile (initial Java MVP)

- Pin JDK image digest and compiler/runtime version; exercise declares allowed language profile.
- Compile and run in separate bounded phases; no package downloads or arbitrary build scripts.
- Disable network, capabilities, privilege escalation, setuid, device access and host namespaces.
- Use non-root UID, restrictive seccomp/AppArmor or equivalent, read-only root, private temporary directory, and process/cgroup isolation.
- Limits are policy configuration, not learner-controlled: initial target 2 vCPU-seconds compile, 1 vCPU-second per test group, 256 MiB memory, 32 processes, 16 MiB scratch, 1 MiB combined output, 5-second wall-clock run; tune through measured workload before production.
- Terminate on deadline and collect only bounded stdout/stderr plus compiler diagnostics.
- Worker image and policy changes require security review and versioned rollout.

These numeric values are proposed initial guardrails, not a production capacity promise.

## API contract (v1 proposal)

### Submit
POST /api/v1/coding-submissions

Request:
```json
{
  "exerciseId": "uuid",
  "language": "JAVA",
  "sourceCode": "public class Solution { ... }",
  "idempotencyKey": "client-generated-uuid"
}
```

Response: 202 Accepted
```json
{
  "submissionId": "uuid",
  "status": "QUEUED",
  "createdAt": "RFC3339 timestamp"
}
```

Validation: source size <= 64 KiB; only published exercises and enabled language profiles; request rate/concurrency quotas enforced. Idempotent replay returns the original submission receipt.

### Status/result
GET /api/v1/coding-submissions/{submissionId}

Statuses: QUEUED, RUNNING, COMPLETED, COMPILE_ERROR, TIME_LIMIT_EXCEEDED, MEMORY_LIMIT_EXCEEDED, CANCELLED, INTERNAL_ERROR.

Result fields: status, public test count/passed count, bounded compiler diagnostics, bounded public-test output, elapsed time and runtime profile version. Never return hidden test names, inputs, expected outputs, sandbox filesystem paths, environment variables or internal stack traces.

### Cancel
POST /api/v1/coding-submissions/{submissionId}/cancel
Only the owner may cancel a queued/running submission. Cancellation is idempotent; terminal submissions return their existing status.

## Data retention and observability

- Store source only for the documented retention period and allow learner deletion/export under the academy privacy policy.
- Keep hidden tests in a separate authoring/release boundary; worker receives test data only for the leased job and never logs it.
- Metrics: queue depth/wait, execution duration, timeout/resource-limit counts, worker crash rate and per-profile cost. Do not use source code or test payloads as metric labels.
- Audit administrative access to source and exercise test bundles.

## MVP acceptance criteria

- Mock executor supports submit, idempotent replay, queued/running/terminal status, cancellation and deterministic sample verdicts.
- Authorization tests cover cross-user and cross-academy access.
- Payload, rate, queue and output bounds are tested.
- OpenAPI contract describes all request/response states and errors.
- No real code execution is enabled by default.
- A real sandbox cannot be enabled until isolation, network denial, resource kill, hidden-test protection and security review are evidenced in automated integration tests.
