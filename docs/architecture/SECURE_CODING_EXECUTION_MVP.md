# Secure coding-practice execution: threat model and MVP contract

Status: architecture decision and implementation handoff for issue #197.

## Security invariant
Never compile or execute learner-submitted source inside the Spring Boot process, a shared application container, or a worker with production credentials. Treat source, stdin, test output, compiler diagnostics, archive contents and requested runtime options as hostile input.

## Trust boundaries
1. Authenticated API accepts a bounded submission and resolves exercise/version from the server-side catalogue. Client-supplied expected answers, hidden tests, runner image, command, classpath and resource limits are ignored.
2. API persists an immutable submission record and places only its opaque ID on a queue. API workers do not execute user code.
3. A dispatcher creates one disposable sandbox per attempt from a pinned, signed Java runtime image. The sandbox has no network interface, no host mount, no cloud metadata route, no application secrets and no Docker socket.
4. A separate privileged supervisor enforces limits and collects a bounded result envelope. Sandbox teardown is mandatory for success, timeout, crash and cancellation.

## MVP execution profile (server-owned)
- Language: Java; fixed supported JDK/compiler version per exercise runtime profile.
- CPU: maximum 2 vCPU; wall-clock compile 8 seconds and execution 3 seconds per test (configurable only by platform policy).
- Memory: 256 MiB; process count: 32; output: 64 KiB per stream; source: 64 KiB; archive: disabled for MVP.
- Filesystem: isolated ephemeral workspace, read-only runtime, writable bounded temp directory; destroy after attempt.
- Network: deny all egress and ingress, including DNS, loopback service access and metadata endpoints.
- Syscalls/capabilities: least privilege, seccomp/AppArmor or equivalent, non-root UID, no privilege escalation.
- Queue: bounded per-user and global concurrency, deadline, retry only for infrastructure failures; never retry a user compile/runtime failure automatically.
- Tests: public sample tests may be returned; hidden tests remain server-side and only aggregate verdict or safe diagnostics leave the sandbox.

These are initial conservative values to benchmark and tune through security review, not client-overridable settings.

## API contract
POST /api/v1/coding/submissions accepts exerciseId, exerciseRevision, language and source; returns 202 with submissionId and QUEUED status. Enforce ownership, published exercise revision, size limits, rate limits and idempotency key.

GET /api/v1/coding/submissions/{id} returns only the authenticated owner's status, timestamps, bounded compile diagnostics and per-public-test result. Never return hidden inputs, expected outputs, runner host details or internal stack traces.

Statuses: QUEUED -> STARTING -> COMPILING -> RUNNING -> COMPLETED; terminal alternatives COMPILE_ERROR, FAILED, TIMEOUT, CANCELLED and INFRA_ERROR. State changes are monotonic and persisted. Cancellation is cooperative at API level and enforced by supervisor termination.

## Data and operational controls
- Store source encrypted at rest with retention and deletion policy; do not include source in application logs, tracing spans or analytics.
- Record runtime image digest, exercise revision, policy version, queue delay, resource usage and terminal status for audit.
- Redact compiler paths and host identifiers. Bound and sanitize diagnostics before rendering.
- Add abuse throttles, quotas, queue backpressure, dead-letter handling and operator kill switch.
- Sandbox compromise is a security incident; isolate execution account and network from production data plane.

## Rollout gates
1. Threat-model review and adversarial test plan.
2. Infrastructure prototype proving network, filesystem and metadata isolation.
3. Supervisor timeout and cleanup tests, including fork bomb, memory exhaustion, infinite loop and output flood.
4. Load/cost benchmark and per-account quotas.
5. Only then enable execution behind a feature flag for internal exercises.

## Deferred
Multi-language runners, package downloads, arbitrary build tools, user-defined dependencies, interactive terminals and unrestricted archives are out of MVP scope. A mock-backed learner flow may ship before a sandbox, but must display that execution is unavailable and must not report a fabricated result.
