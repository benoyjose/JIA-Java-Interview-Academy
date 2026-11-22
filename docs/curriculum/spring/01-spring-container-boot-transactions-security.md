# Advanced Spring: Container Internals, Spring Boot, Web, Transactions and Security

> **Editorial status:** Draft for technical review; not published.
> **Audience:** Senior Java / Tech Lead / Architect interviews
> **Baseline:** Spring Framework 6.x and Spring Boot 3.x concepts; validate exact behavior against the selected patch release and dependency set.
> **Prerequisites:** Java reflection, annotations, proxies, HTTP, transactions and basic dependency injection.

## Learning outcomes

- Explain how the Spring container discovers, creates, wires and manages beans.
- Diagnose bean lifecycle, scope, circular dependency and auto-configuration behavior.
- Trace an HTTP request through the servlet container, filters, Spring MVC and controller.
- Explain proxy-based AOP and transaction boundaries, including self-invocation pitfalls.
- Design production-ready REST APIs, validation and error contracts.
- Describe Spring Security's filter chain and secure authentication/authorization.
- Configure health, metrics, tracing, graceful shutdown and deployment readiness.

## Interview answer

Spring's IoC container manages object construction and dependency wiring through BeanDefinitions and the ApplicationContext. Spring Boot layers opinionated auto-configuration and externalized configuration on top, conditional on classpath, properties and existing beans. In a typical servlet application, requests pass through the servlet container and filter chain, then DispatcherServlet selects a handler and invokes controller/service code. Many Spring cross-cutting features—such as declarative transactions—are implemented through proxies, so calls must cross the proxy boundary for advice to apply. I design explicit transaction scopes, constructor-injected components, validated DTOs, consistent error responses and least-privilege security. For production, readiness, metrics, tracing, graceful shutdown and configuration hygiene are part of the application design, not deployment afterthoughts.

## 1. IoC container and bean lifecycle

### Bean definitions and ApplicationContext

The container does not simply scan classes and instantiate everything immediately. It registers BeanDefinitions describing how a bean should be created, configured and managed. Definitions may originate from component scanning, Java configuration, XML in legacy systems, imports, auto-configuration or programmatic registration.

The ApplicationContext adds application-level services such as events, resource loading and integration with the environment. BeanFactory is the underlying container abstraction; ApplicationContext is the common application-facing container.

### Lifecycle at a conceptual level

A typical singleton creation path includes:

1. Resolve the BeanDefinition and constructor/factory method.
2. Instantiate the object.
3. Populate dependencies and properties.
4. Apply aware callbacks where applicable.
5. Run BeanPostProcessors before initialization.
6. Invoke initialization callbacks such as configured init methods or InitializingBean.
7. Run BeanPostProcessors after initialization; this is a common point for proxy creation.
8. Publish the fully initialized singleton to the container.

Exact callback order depends on configuration and extension points. Learn the lifecycle contract, then inspect the actual configured post-processors when debugging.

### Constructor injection

Prefer constructor injection for required dependencies:

```java
@Service
public class OrderService {
    private final OrderRepository repository;
    private final PaymentClient paymentClient;

    public OrderService(OrderRepository repository,
                        PaymentClient paymentClient) {
        this.repository = repository;
        this.paymentClient = paymentClient;
    }
}
```

It makes dependencies explicit, supports immutable fields, simplifies unit testing and reveals cycles early. Use optional/lazy/provider-based dependencies only when the lifecycle or architecture genuinely requires them; they should not conceal poor service boundaries.

### Scopes

Singleton is the default Spring bean scope: one managed instance per bean definition per container, not one instance globally across all JVMs. Prototype creates a new instance when requested, but the container does not manage the full destruction lifecycle of prototype objects after handing them to the caller. Web scopes require a web-aware context.

A singleton that depends directly on a prototype generally receives one prototype instance at singleton creation. Use a scoped proxy or ObjectProvider when repeated retrieval is required, and document the lifecycle.

## 2. Configuration and auto-configuration

### Java configuration

`@Configuration` classes and `@Bean` methods declare bean definitions. In full configuration mode, method calls between bean methods can be intercepted to preserve container semantics. In lite mode (for example, certain component-scanned configuration classes), direct method calls behave like ordinary Java calls. Prefer method-parameter injection when one bean method depends on another.

### Spring Boot auto-configuration

Auto-configuration provides defaults based on conditions such as:

- Classes present or absent on the classpath.
- Properties set in the Environment.
- Beans already registered.
- Web application type and other context conditions.

Auto-configuration is not magic and should be diagnosable. Use condition evaluation reports, startup logs and Actuator/configuration diagnostics appropriate to the environment. Avoid fighting defaults with broad component scans or duplicate bean definitions; add explicit configuration narrowly.

### Externalized configuration

Use typed configuration properties for structured settings:

```java
@ConfigurationProperties(prefix = "service.payment")
public record PaymentProperties(
        URI endpoint,
        Duration timeout,
        int maxAttempts) {}
```

Validate required values and sensible bounds at startup. Keep secrets in a secret-management mechanism/environment configuration, not source control or logs. Distinguish deployment configuration from business data.

## 3. AOP and proxy mechanics

Spring AOP commonly uses JDK dynamic proxies for interface-based proxying and class-based proxies when configured/required. Proxy behavior affects method visibility, final methods/classes, identity and call paths.

Cross-cutting advice applies when invocation passes through the proxy. A call from one method to another on the same target object—**self-invocation**—does not normally re-enter the proxy:

```java
@Service
class BillingService {
    public void process() {
        persist(); // direct this-call; transactional proxy may not intercept
    }

    @Transactional
    public void persist() {
        // ...
    }
}
```

Better options are to place the transactional boundary on the externally invoked method, extract the transactional operation into a separate bean, or use a deliberate programmatic transaction boundary. Avoid injecting a bean into itself merely to bypass an architectural problem.

## 4. Transaction semantics

### What @Transactional actually means

Declarative transaction management typically uses an interceptor around a method invocation. The transaction manager begins, joins, commits or rolls back according to the configured propagation and rules.

Important interview points:

- Default proxy mode means calls must cross the proxy.
- Default rollback behavior traditionally rolls back for unchecked exceptions and Errors; checked exceptions require explicit rules or a different policy.
- Transaction context is generally thread-bound for imperative transaction managers; moving work to another thread does not automatically carry the transaction.
- A transaction does not automatically include remote HTTP calls or a second independent service.
- Database isolation and locking behavior still depend on the database and transaction manager.
- A transaction around a long-running remote call can hold locks/connections and harm throughput.

### Propagation and isolation

- REQUIRED: join existing transaction or create one.
- REQUIRES_NEW: suspend current transaction and create an independent one (resource/connection implications).
- SUPPORTS, MANDATORY, NOT_SUPPORTED, NEVER and NESTED have distinct semantics; verify manager support, particularly savepoint behavior for NESTED.

Isolation levels express database concurrency behavior. Do not promise that every database implements them identically. Understand dirty reads, non-repeatable reads, phantom reads, MVCC and lock behavior for the target engine.

### Transactional design example

For a database write followed by event publication, a direct “commit DB then publish message” sequence can lose the event if the process crashes between steps. Use a transactional outbox: persist the domain change and outbox record in the same database transaction, then publish asynchronously with idempotent handling. A local @Transactional annotation does not create a distributed atomic transaction across DB and broker.

## 5. Spring MVC request lifecycle

A simplified servlet request path:

1. Client request reaches the servlet container and configured servlet filters.
2. Spring Security's filter chain evaluates security rules.
3. DispatcherServlet receives the request.
4. HandlerMapping identifies a controller/handler.
5. HandlerAdapter invokes it with argument resolution and conversion.
6. Validation and controller/service logic run.
7. Return values are handled by message converters/view resolution.
8. Exception resolvers and filters may transform the response.

Filters operate at the servlet boundary; HandlerInterceptors operate around Spring MVC handler execution. Choose the layer that matches the requirement. Do not use an interceptor as a replacement for security controls that belong in Spring Security.

### REST API quality

- Use HTTP semantics and status codes consistently.
- Validate request DTOs at the boundary; do not bind arbitrary client input directly to persistence entities.
- Keep error responses stable and machine-readable; include a correlation identifier, not sensitive stack traces.
- Define pagination, sorting limits, filtering and versioning policy.
- Make retry behavior safe for operations that may be repeated; use idempotency keys for relevant workflows.
- Document contracts with OpenAPI and test backward compatibility.

## 6. Validation and exception handling

`@Valid` triggers Bean Validation where configured; constraints should express request invariants. Validation is not authorization and does not replace domain validation.

Use centralized exception mapping (for example, `@RestControllerAdvice`) for predictable API errors. Separate:
- Client input errors.
- Authentication/authorization failures.
- Missing resources and conflict states.
- Unexpected server errors.

Never return internal SQL, secrets, stack traces or provider tokens to clients. Log diagnostic detail with access controls and correlation context.

## 7. Spring Security architecture

Spring Security's servlet integration is organized around a filter chain. Authentication establishes a principal; authorization decides whether the principal may perform an action. Keep those responsibilities distinct.

### Core design

- Define explicit request authorization rules; avoid accidentally broad permit-all patterns.
- Use method security for domain-level authorization where it adds defense in depth.
- Validate JWT issuer, audience, signature, expiration and key rotation according to the identity provider contract.
- Prefer OAuth2/OIDC standards and managed identity flows over custom token formats.
- Keep authentication secrets out of logs and client-visible errors.
- Apply least privilege to roles/scopes and enforce tenant boundaries in data access, not only in UI filtering.

### Stateless does not mean unsecured

A stateless API may avoid server-side session storage, but still requires token validation, authorization, TLS, input validation, rate limits and careful CORS policy. CORS is a browser cross-origin policy, not an authentication mechanism.

## 8. Spring Data and persistence boundary

Repository abstractions reduce boilerplate but do not remove SQL or ORM costs. Understand the query generated, transaction scope, fetch plan and database execution plan.

- Avoid exposing persistence entities as API contracts.
- Use DTO projections for read paths with known shape.
- Review lazy loading and N+1 queries.
- Keep transactions around cohesive database work.
- Avoid holding a persistence context open across slow network operations.
- Use optimistic locking/version fields for appropriate concurrent updates.
- Apply migrations through a controlled schema-versioning tool and test forward-compatible deployment sequences.

## 9. Production readiness

### Actuator and health

Use health/readiness/liveness signals intentionally. Liveness should usually answer whether the process should be restarted; readiness should indicate whether it can safely receive traffic. Avoid making liveness depend on a transient downstream service in a way that creates restart storms.

### Observability

Instrument:
- Request rate, errors and latency (including percentiles).
- JVM heap, allocation, GC and thread metrics.
- Database connection pool and query latency.
- Executor/queue saturation.
- External dependency latency and failure rate.

Use consistent trace/correlation identifiers. Do not put unbounded or sensitive identifiers into metric labels.

### Graceful shutdown

On termination, stop accepting new work, allow in-flight requests/tasks a bounded drain period, close resources and respect orchestrator termination deadlines. Validate shutdown behavior in the deployment environment; local process exit tests are not enough.

## 10. Common Spring interview traps

- Singleton means one instance across all servers — **false**.
- `@Transactional` works on every method call regardless of proxy — **false**.
- A transaction automatically covers HTTP calls and Kafka publishing — **false**.
- Constructor injection is only a style preference — **false**; it exposes required dependencies and cycles.
- CORS provides API security — **false**.
- Liveness and readiness are interchangeable — **false**.
- Spring Boot auto-configuration cannot be inspected — **false**; condition reports and startup diagnostics exist.
- Lazy JPA relationships are always safe to access in controllers — **false**; context boundaries and N+1 matter.
- A stateless JWT API requires no authorization state or revocation design — **false**; token lifetime, issuer, audience, scopes and revocation strategy matter.

## 11. Hands-on scenarios

1. **Bean cycle:** Diagnose two constructor-injected services depending on each other. Refactor responsibility or introduce an appropriate event/interface boundary; do not default to field injection.
2. **Transaction not applied:** Reproduce self-invocation and verify proxy behavior. Move the boundary and test rollback.
3. **Slow endpoint:** Instrument controller/service/repository/dependency timings; inspect SQL and connection-pool saturation before increasing thread count.
4. **Security review:** Review a JWT resource server configuration for issuer/audience, scope mapping, endpoint authorization, tenant isolation and secret exposure.
5. **Graceful deploy:** Demonstrate readiness transition, stop accepting new traffic, drain active requests and close executor/database resources within a timeout.

## 12. Senior interview questions

1. Walk through Spring bean creation and where proxies may be introduced.
2. What causes a circular dependency, and why can constructor injection reveal it?
3. Full vs lite @Configuration behavior?
4. How does Spring Boot decide whether an auto-configuration applies?
5. Why does self-invoked @Transactional not behave as expected?
6. How do propagation and isolation differ?
7. Why should remote calls usually not be inside a database transaction?
8. Filter vs HandlerInterceptor vs controller advice?
9. How do you design an idempotent API for client retries?
10. How do authentication and authorization differ in Spring Security?
11. How do you distinguish liveness from readiness?
12. How would you investigate connection-pool exhaustion and N+1 queries in production?

## Reviewer references

- Spring Framework reference: https://docs.spring.io/spring-framework/reference/
- Spring Boot reference: https://docs.spring.io/spring-boot/docs/3.4.x/reference/html/
- Spring transaction management: https://docs.spring.io/spring-framework/reference/data-access/transaction.html
- Spring Security reference: https://docs.spring.io/spring-security/reference/
- Spring MVC reference: https://docs.spring.io/spring-framework/reference/web/webmvc.html

**Editorial gate:** Align exact version claims to the project's supported Spring release, validate sample code/configuration, and complete technical review before import or publication.