# Coding Exercise Execution: Threat Model and MVP Boundary

This is a security architecture gate for issue #197. Do not execute untrusted submissions in the Spring Boot or Laravel application process, and do not treat a container alone as a sufficient sandbox.

## Assets and trust boundaries
Assets: platform credentials, learner data, hidden test cases, scoring integrity, worker capacity, and host/cloud control plane. Treat submitted source, compiler output, runtime output and user-provided test input as hostile.

## Threats
- Escape from process/container to host or other tenant.
- Network access to metadata services, internal APIs, databases or internet endpoints.
- Filesystem traversal, secret discovery, fork bombs, memory exhaustion, CPU starvation and oversized output.
- Compiler/runtime exploit, malicious annotation processing or dependency resolution.
- Hidden-test exfiltration, score tampering, replay and denial of service.
- Abuse of queue, API, storage or cost budgets.

## Required isolation controls
- Ephemeral single-job execution environment, non-root UID, read-only base filesystem and disposable workspace.
- No network egress, no cloud metadata access, no mounted application secrets, no Docker socket, no host paths.
- Strict CPU, memory, process-count, wall-clock, disk and stdout/stderr limits.
- Pin compiler/runtime images by digest; no arbitrary dependency downloads or annotation processing.
- Separate worker identity and network policy from API/database services.
- Destroy workspace after job; retain only bounded, sanitized result metadata.
- Hidden tests remain server-side and are injected only into the isolated execution context.

## MVP architecture
API validates request and entitlement, stores submission metadata, and enqueues a job. A scheduler dispatches to an isolated worker pool. Worker compiles/runs against a fixed JDK image and test harness, emits a signed/bounded result, and is destroyed. API retrieves result by opaque job ID with ownership checks.

## Operational controls
Per-account quotas, concurrency caps, queue backpressure, abuse detection, cost dashboards, cancellation, and worker health. Fail closed if isolation prerequisites are unavailable. Do not enable production code execution until security review and penetration tests pass.

## Acceptance
Threat-model review, abuse cases, sandbox prototype with negative tests for network/filesystem/CPU/memory escape attempts, and documented residual risks.