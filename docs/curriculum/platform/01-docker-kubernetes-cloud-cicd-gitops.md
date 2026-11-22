# Production Delivery: Docker, Kubernetes, AWS, CI/CD, GitOps and Release Safety

> **Status:** Draft for technical review; not published.
> **Audience:** Senior Java Backend / Platform / Tech Lead / Architect
> **Version scope:** Concepts are broadly applicable; validate Kubernetes, AWS service and CI/CD syntax against current deployed versions.

## Outcomes

Build reproducible container images; understand Kubernetes reconciliation and workload networking; configure probes, requests/limits, autoscaling and safe rollout; design cloud IAM/network/storage boundaries; create auditable CI/CD and GitOps pipelines; and plan rollback, database compatibility and supply-chain security.

## Interview-ready summary

I treat production delivery as a controlled path from source to a verified artifact to environment promotion. CI builds and tests immutable artifacts; CD promotes the same digest through environments with approvals and policy checks. Containers package the application but do not replace runtime configuration, security or observability. Kubernetes continuously reconciles declared desired state, while probes, resource requests/limits, disruption budgets and rollout strategy determine how safely it operates under failure. For Java services I account for JVM heap/native memory within container limits, graceful shutdown, readiness and dependency saturation. Release safety includes backward-compatible schema changes, canary metrics, automated rollback criteria and a tested recovery path.

## 1. Docker image fundamentals

### Image and container

An image is an immutable layered filesystem plus metadata; a container is a runtime instance with namespaces, cgroups and an isolated process environment. Layers are reused across builds and pulls, but excessive layers/files increase transfer and attack surface.

### Multi-stage build

Separate compilation from runtime to keep build tools and caches out of production images. Use a minimal trusted base, pin versions/digests where reproducibility requires it, and copy only necessary artifacts.

### Container best practices

- Run as a non-root user.
- Use a small, maintained base image.
- Avoid embedding credentials or environment-specific configuration.
- Use .dockerignore to exclude secrets, local build output and repository metadata.
- Prefer deterministic builds and explicit entrypoints.
- Handle SIGTERM and exit promptly after graceful drain.
- Scan image and dependencies; define patch cadence and provenance.

A container is not a security boundary by itself. Runtime capabilities, host kernel, network policy and identity permissions matter.

## 2. Kubernetes control model

Kubernetes desired state is declared through API objects. Controllers reconcile actual state toward desired state; a Deployment manages ReplicaSets and Pods, while the scheduler assigns Pods to nodes.

### Core resources

- **Pod:** scheduling and runtime unit; not usually managed directly for stateless app rollout.
- **Deployment:** stateless replica and rollout management.
- **StatefulSet:** stable identity/storage patterns; not a replacement for a managed database without careful operations.
- **Service:** stable virtual endpoint for a set of Pods.
- **Ingress/Gateway:** HTTP routing at cluster edge, depending on installed controller.
- **ConfigMap/Secret:** configuration and secret references; protect secrets with encryption/access controls.
- **Job/CronJob:** bounded or scheduled batch execution.
- **HPA:** adjusts replicas based on metrics.
- **PDB:** constrains voluntary disruption; it does not guarantee application availability.

### Reconciliation and rollout

A Deployment controller replaces Pods gradually according to strategy and availability settings. Rollout can stall if readiness fails, resource constraints prevent scheduling, image pull fails or startup exceeds configured deadlines.

Use immutable image tags/digests and record the artifact in deployment metadata. Avoid relying on mutable `latest`.

## 3. Probes: startup, readiness and liveness

- **Startup probe:** gives slow-starting application time before liveness/readiness checks become active.
- **Readiness probe:** whether this instance should receive new traffic.
- **Liveness probe:** whether the process is stuck and should be restarted.

Do not make liveness depend on a transient database or remote service; that can trigger restart storms. Readiness may include critical dependencies if traffic cannot be served safely, but use timeouts and avoid cascading unready-all behavior.

A probe should be cheap, bounded and represent the intended operational contract.

## 4. Resource requests, limits and Java memory

Requests influence scheduling; limits constrain resource use according to runtime policy. CPU throttling can increase latency even when CPU metrics appear below application-level saturation. Memory limit breach can cause OOMKilled.

For Java, container memory includes:
- Heap.
- Metaspace and code cache.
- Thread stacks.
- Direct buffers and native libraries.
- GC/runtime overhead.

Do not set `-Xmx` equal to the container limit. Reserve native headroom and validate effective JVM ergonomics for the deployed JDK. Monitor RSS/container working set and heap separately.

### Autoscaling

HPA is only useful when the scaling signal maps to capacity pressure and new replicas can become ready quickly. CPU-based scaling may miss queue backlog or DB bottlenecks. Scale on queue age/concurrency when appropriate, but cap replicas based on downstream capacity and connection-pool budgets.

## 5. Networking, storage and workload boundaries

- ClusterIP exposes service internally.
- LoadBalancer and ingress/gateway expose traffic externally according to platform.
- NetworkPolicy can restrict pod traffic when supported by the CNI.
- PersistentVolume/PersistentVolumeClaim provide storage abstraction; durability, performance and backup are storage-class/provider dependent.

Do not assume Pod IP stability. Use Services and DNS. Define egress rules for external APIs, metadata endpoints and control services.

## 6. AWS architecture fundamentals

Choose managed services based on operational ownership and constraints:
- Compute: EC2, ECS, EKS, Lambda depending on workload.
- Object storage: S3 for durable objects, lifecycle and versioning policies.
- Relational: RDS/Aurora with backup, failover and connection management.
- Networking: VPC, subnets, route tables, security groups, load balancers and private endpoints.
- Identity: IAM roles and short-lived credentials.
- Observability: CloudWatch and integrated telemetry.

Use private subnets for workloads/data where appropriate, least-privilege security groups, controlled egress and multi-AZ design for availability. A managed service reduces operational tasks but does not remove capacity, cost, security or recovery responsibility.

## 7. IAM and secrets

Prefer workload identity/roles over long-lived access keys embedded in containers. Separate roles for CI, deployment, runtime and data access.

Secrets:
- Store in an appropriate secret manager.
- Restrict access by identity and environment.
- Rotate and audit.
- Avoid exposing values through environment dumps, logs, build args or artifacts.
- Validate rotation behavior without requiring an unsafe restart of every service simultaneously.

## 8. CI pipeline and artifact integrity

A robust CI sequence:
1. Checkout exact commit.
2. Verify dependency and tool versions.
3. Compile and run unit tests.
4. Run static analysis, lint and secret scanning.
5. Run integration/contract tests where appropriate.
6. Build container/APK/package once.
7. Scan and attest artifact.
8. Publish immutable artifact with commit, version and digest.
9. Store reports and provenance.

CI should fail deterministically on required checks. Flaky tests need diagnosis and ownership, not permanent silent retries.

## 9. CD, GitOps and environment promotion

### Continuous delivery

Promote the same artifact from development to staging to production; do not rebuild with different source per environment. Environment differences belong in controlled configuration.

### GitOps

A GitOps controller reconciles desired deployment configuration from a version-controlled source. Benefits include review, audit and drift detection. Risks include secret leakage, broad controller permissions and unclear emergency changes.

Define:
- Repository/environment ownership.
- Approval and promotion policy.
- Drift alert and reconciliation.
- Break-glass workflow and post-incident reconciliation.
- Rollback semantics and artifact retention.

### Jenkins, GitHub Actions, Argo CD and Spinnaker

Tool choice is less important than pipeline semantics: reproducibility, permissions, artifact immutability, promotion, approvals, audit and rollback. Avoid duplicate competing deploy controllers for the same resource.

## 10. Deployment strategies

### Rolling

Gradually replaces instances. Requires compatibility between old and new versions and capacity to run both during rollout.

### Blue-green

Two environments; traffic switches after verification. Provides fast switchback but requires duplicate capacity and state compatibility.

### Canary

Routes a small fraction of traffic to a new version and evaluates defined metrics before expanding. Select metrics sensitive to regression: error rate, p95/p99, saturation and business outcome.

### Rollback

Rollback code does not automatically roll back database changes or external side effects. Prefer forward-compatible expand/migrate/contract schema changes and reversible configuration. Define automatic rollback thresholds and manual override authority.

## 11. Database and message compatibility during releases

A safe migration sequence:
1. Add new nullable/compatible schema.
2. Deploy code that reads old/new and writes compatible representation.
3. Backfill with bounded, resumable job.
4. Switch reads/writes.
5. Verify no old code depends on prior shape.
6. Remove old schema in a later release.

Use the same compatibility discipline for event schemas and API contracts. Deployment order is a correctness constraint in distributed systems.

## 12. Reliability and disruption controls

- Graceful termination and bounded request drain.
- Pod anti-affinity/topology spread for failure-domain diversity.
- PDB for planned node maintenance.
- Readiness gates and rollout maxUnavailable/maxSurge.
- Capacity headroom during node/zone failure.
- Rate limits and queue backpressure during partial outage.
- Backup restore and failover drills.

A PDB cannot compensate for a single replica, poor readiness or a shared dependency failure.

## 13. Production troubleshooting

| Symptom | Investigate |
|---|---|
| Pod CrashLoopBackOff | Exit code, logs, startup config, probe and dependency behavior |
| OOMKilled | Container RSS, heap/native, memory limit, leak/allocation profile |
| Pending Pod | Requests, node capacity, affinity, PVC and quota |
| Rollout stuck | Readiness, image pull, startup, maxUnavailable, scheduling |
| High p99 with CPU throttling | CPU limit, concurrency, JVM/GC and downstream saturation |
| HPA does not scale | Metrics adapter, target metric, requests, scaling stabilization |
| Deployment drift | GitOps source, controller reconciliation and emergency changes |
| CI artifact differs from release | Rebuild/promote workflow, digest and provenance |
| Database migration blocks release | Lock duration, old/new compatibility, backfill and rollback plan |

## 14. Exercises

1. Write a multi-stage Dockerfile for a Spring Boot service and explain runtime image contents.
2. Design Deployment, Service and probes for a JVM API; calculate memory headroom.
3. Diagnose a Pod OOMKilled while Java heap appears below maximum.
4. Design GitOps promotion with immutable digest, approval and drift detection.
5. Plan canary metrics and rollback thresholds for a latency-sensitive endpoint.
6. Design expand-contract migration compatible with two concurrently running application versions.
7. Define AWS IAM roles for CI deployer, runtime service and read-only diagnostics.

## 15. Senior / architect interview questions

- Image vs container and why multi-stage builds?
- How does Kubernetes reconciliation differ from imperative deployment?
- Liveness vs readiness vs startup probe?
- How do requests and limits affect scheduling and throttling?
- How do you budget JVM memory inside a Pod?
- Why can HPA scale worsen database overload?
- How do you design least-privilege workload identity?
- What does GitOps solve, and what does it not solve?
- Compare rolling, blue-green and canary.
- How do you roll back a release with a schema change?
- What metrics should gate a canary?
- How do you test disaster recovery and backup restore?
- What does artifact provenance mean in a supply-chain review?

## References

- Docker build best practices: https://docs.docker.com/build/building/best-practices/
- Kubernetes concepts: https://kubernetes.io/docs/concepts/
- Kubernetes probes: https://kubernetes.io/docs/concepts/configuration/liveness-readiness-startup-probes/
- Kubernetes resource management: https://kubernetes.io/docs/concepts/configuration/manage-resources-containers/
- AWS Well-Architected: https://docs.aws.amazon.com/wellarchitected/latest/framework/welcome.html
- Argo CD documentation: https://argo-cd.readthedocs.io/en/stable/
- SLSA supply-chain framework: https://slsa.dev/

**Editorial gate:** Validate manifests and commands against selected cluster/runtime versions, check security defaults, and review release examples before publication.