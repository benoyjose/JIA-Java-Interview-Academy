# Advanced Java: Java Memory Model, Happens-Before and Safe Publication

> **Editorial status:** Draft for technical review. Not published learning content.
> **Target:** Senior Java Engineer / Tech Lead / Staff-Principal interviews
> **Language baseline:** Java 17; notes call out behavior that is not tied to a specific release.
> **Prerequisites:** Threads, object references, synchronization, basic JVM runtime areas.

## Learning outcomes

After this lesson, a learner should be able to:

- Explain why source-code order, thread execution order and observed order can differ.
- Define a data race and distinguish visibility, ordering and atomicity.
- Use the happens-before relation to prove correctness rather than relying on timing.
- Choose between `volatile`, intrinsic monitors, locks, atomics and concurrent utilities.
- Recognize unsafe publication, constructor escape and double-checked-locking mistakes.
- Diagnose a concurrency failure with a minimal reproducer and JVM tooling.

## Interview answer (60–90 seconds)

The Java Memory Model defines the legal interactions between threads and memory. A thread can cache or reorder operations as long as single-thread behavior and the memory-model rules are respected, so unsynchronized communication can produce stale or surprising observations. Correct concurrent code establishes a **happens-before** relationship. Program order applies within one thread; unlocking a monitor happens-before a later lock of that same monitor; a volatile write happens-before a subsequent read of that field; and thread start, termination detection and concurrency-library contracts provide additional edges. I use volatile for a simple visibility/order signal, synchronization or locks for compound invariants, atomics for supported atomic operations, and concurrent collections for shared data structures. I avoid “it usually works” reasoning and prove the required ordering.

## 1. Three separate properties

Concurrency questions often mix three properties:

| Property | Meaning | Typical failure |
|---|---|---|
| Visibility | One thread observes another thread's completed write | Stale value |
| Ordering | Operations occur in an order required by the protocol | Flag becomes visible before associated data |
| Atomicity | An operation is indivisible relative to competing operations | Lost increment or partial invariant |

A construct can solve one property without solving all three. For example, volatile gives visibility and ordering guarantees for accesses to that variable, but `count++` remains a read-modify-write sequence and is not atomic.

## 2. What the Java Memory Model guarantees

The JMM is a specification of allowed outcomes, not a promise that every CPU or JVM behaves identically. Compiler and processor optimizations may reorder or retain values when the program has no synchronization that constrains those operations.

### Program order

Within one thread, actions are ordered according to the program's execution semantics. This does not automatically establish ordering between two different threads.

### Synchronization order

Synchronization actions (such as monitor operations and volatile accesses) participate in a synchronization order consistent with each thread's program order. Correct reasoning should use the specified synchronizes-with edges and transitivity, not assumptions about wall-clock timing.

### Happens-before

Happens-before is a **partial order**. If action A happens-before action B, the effects of A are guaranteed visible to B under the JMM. The relation is transitive: if A happens-before B and B happens-before C, then A happens-before C.

Important edges:

- **Program order:** earlier actions in a thread happen-before later actions in that thread.
- **Monitor:** an unlock of monitor M happens-before every subsequent lock of M.
- **Volatile:** a write to volatile variable V happens-before every subsequent read of V in synchronization order.
- **Thread lifecycle:** calling `Thread.start()` happens-before actions in the started thread; actions in a thread happen-before another thread successfully returns from `join()` on it.
- **Executor/Future:** task submission and result retrieval have documented memory-consistency effects. Use the relevant API contract rather than assuming every library method creates an edge.
- **Class initialization:** successful class initialization safely establishes visibility of initialized static state to threads using the class.

A happens-before edge is stronger and more useful than saying one operation “probably runs first.”

## 3. Data races and why timing tests are weak

A data race exists when conflicting accesses to the same variable (at least one write) are not ordered by happens-before. In a racy program, the JMM permits outcomes that a developer may not expect.

Consider:

```java
final class StopFlag {
    private boolean stopped;

    void stop() {
        stopped = true;
    }

    void run() {
        while (!stopped) {
            // Work
        }
    }
}
```

The writer and reader access `stopped` without synchronization. The loop is not guaranteed to observe the update promptly—or at all. Adding a sleep does not repair the data race; it changes timing, not the memory-model contract.

Make the communication explicit:

```java
final class StopFlag {
    private volatile boolean stopped;

    void stop() {
        stopped = true;
    }

    void run() {
        while (!stopped) {
            // Work
        }
    }
}
```

This is suitable because the flag is an independent state signal. If stopping must also atomically update several fields, use a lock or a state object with a well-defined synchronization protocol.

## 4. Volatile: visibility and ordering, not a general lock

A volatile read observes a volatile write according to the JMM's synchronization rules. Volatile also constrains reordering around the access sufficiently to support publication patterns.

It does **not** make compound operations atomic:

```java
volatile int count = 0;

void increment() {
    count++; // read, add, write; concurrent increments can be lost
}
```

Use `AtomicInteger.incrementAndGet()` when a standalone atomic counter is the requirement, or a lock when the counter participates in a larger invariant.

### Volatile publication example

```java
final class Configuration {
    int port;
    String host;
}

private Configuration configuration;
private volatile boolean initialized;

void initialize() {
    Configuration c = new Configuration();
    c.port = 8080;
    c.host = "localhost";

    configuration = c;
    initialized = true; // volatile release-style publication
}

Configuration readConfiguration() {
    if (initialized) { // volatile read
        return configuration;
    }
    return null;
}
```

The volatile write to `initialized` happens-before a subsequent read that observes it. Writes performed before that volatile write become visible to the reader after the corresponding read. Do not subsequently mutate the published object without another synchronization strategy.

## 5. Monitors and locks

A synchronized block provides mutual exclusion for code guarded by the same monitor and establishes visibility through unlock/lock.

```java
final class Inventory {
    private int available;

    synchronized boolean reserve(int quantity) {
        if (quantity <= 0 || available < quantity) {
            return false;
        }
        available -= quantity;
        return true;
    }

    synchronized int available() {
        return available;
    }
}
```

The check and decrement are one critical section. Synchronizing only the decrement would not protect the invariant.

Practical rules:

- Every access to an invariant's mutable state must follow the same locking discipline.
- Lock the same object consistently; locking different wrappers or newly created objects does not coordinate.
- Keep critical sections bounded; avoid blocking I/O or callbacks while holding a lock.
- Intrinsic monitors are reentrant.
- For explicit locks, use `try/finally` around unlock and document fairness/interruptibility decisions.

## 6. Safe publication

Safe publication means another thread receives a reference through a mechanism that establishes the necessary visibility guarantees.

Common safe mechanisms include:

- Storing a reference in a properly synchronized or volatile field.
- Publishing through a thread-safe collection or a documented concurrent API.
- Initializing a static field during class initialization.
- Constructing an object before starting a thread that consumes it.
- Returning a result through a Future with its documented completion semantics.

### Unsafe publication

```java
// Thread A
shared = new Service();

// Thread B
if (shared != null) {
    shared.execute();
}
```

Without a publication edge, Thread B's non-null observation alone is not a proof that it sees all constructor writes. A reference can become reachable without a reliable visibility protocol.

### Constructor escape

Avoid exposing `this` before construction completes:

```java
final class Listener {
    Listener(Registry registry) {
        registry.register(this); // may expose partially initialized state
        // remaining initialization
    }
}
```

Prefer completing construction first and registering the fully initialized instance afterward.

## 7. Double-checked locking

The following pattern is incorrect when the reference is not volatile:

```java
private static Service instance;

static Service getInstance() {
    if (instance == null) {
        synchronized (Service.class) {
            if (instance == null) {
                instance = new Service();
            }
        }
    }
    return instance;
}
```

The reference must be volatile for the modern double-checked locking pattern:

```java
private static volatile Service instance;

static Service getInstance() {
    Service result = instance;
    if (result == null) {
        synchronized (Service.class) {
            result = instance;
            if (result == null) {
                result = new Service();
                instance = result;
            }
        }
    }
    return result;
}
```

The local variable reduces repeated volatile reads. In application code, prefer dependency injection or the initialization-on-demand holder idiom when appropriate; do not introduce a singleton solely to demonstrate concurrency.

## 8. Final fields and immutability

A correctly constructed object with final fields receives special JMM initialization guarantees, provided the constructor does not let `this` escape. This is useful for immutable value objects.

```java
public final class Endpoint {
    private final String host;
    private final int port;

    public Endpoint(String host, int port) {
        this.host = Objects.requireNonNull(host);
        this.port = port;
    }
}
```

Final-field semantics do not make the entire object graph deeply immutable, do not protect later mutation of referenced objects, and do not replace a general safe-publication protocol for mutable state. Defensive copies are required for mutable inputs and outputs.

## 9. Choosing the right primitive

| Requirement | Preferred starting point | Important limitation |
|---|---|---|
| Independent stop/cancel signal | volatile boolean or atomic flag | Does not protect a multi-field invariant |
| Atomic numeric update | AtomicInteger/LongAdder as workload dictates | LongAdder is not an instantaneous single-value transaction |
| Several fields change together | synchronized or Lock | All participants must use the same lock |
| Shared map/set/queue | Concurrent collection | Understand per-operation and compound-operation guarantees |
| Producer-consumer handoff | BlockingQueue | Capacity and shutdown protocol still matter |
| Task result and completion | Future/CompletableFuture | Handle exceptions, cancellation and timeouts |
| Read-mostly immutable configuration | Immutable snapshot + safe publication | Replace snapshots atomically; avoid mutating after publication |

## 10. Production debugging workflow

When a concurrency defect appears:

1. Define the invariant and the exact shared variables involved.
2. Identify every reader and writer; classify each access as guarded, volatile, atomic or racy.
3. Draw the required happens-before graph. If no path exists, do not rely on observed timing.
4. Reduce the defect to a small deterministic stress test; use repeated runs as evidence, not proof.
5. Capture thread dumps (`jstack` or `jcmd Thread.print`) for deadlock, blocked threads and lock contention.
6. Use Java Flight Recorder for thread, lock and allocation events; use profiler/benchmark tools only with a sound measurement design.
7. Inspect executor queueing, pool sizing, blocking calls and shutdown behavior.
8. Fix the protocol first; tune performance only after correctness is established.

## 11. Common interview traps

- “Volatile makes the operation atomic.” **False** for compound read-modify-write operations.
- “A sleep creates visibility.” **False**; sleep is not a synchronization edge.
- “If a reference is non-null, the object is initialized for every thread.” **Not necessarily** without safe publication.
- “Synchronized methods lock the class.” **False**; instance methods lock `this`, static synchronized methods lock the `Class` object.
- “Final makes an object immutable.” **False**; final reference assignment and object mutability are different.
- “ConcurrentHashMap makes a sequence of map operations atomic.” **False** unless a specific API operation provides the needed compound guarantee.
- “The JMM only matters on weak CPUs.” **False**; compiler/JIT optimizations also matter.

## 12. Practice exercises

### Exercise A — Prove the publication

Given writer actions `data = 42` then `ready = true`, and reader actions `if (ready) print(data)`, explain which declaration of `ready` makes the intended guarantee and why. State what changes if `data` is volatile instead.

**Expected reasoning:** volatile `ready` establishes the publication edge for preceding writes; volatile `data` alone does not make observing `ready` a protocol unless the reader actually reads the corresponding volatile value and the design accounts for the ordering.

### Exercise B — Repair the counter

Implement a thread-safe counter used by several workers. Compare synchronized, AtomicLong and LongAdder. Explain when each is appropriate and whether a snapshot is exact during concurrent updates.

### Exercise C — Review a singleton

Review a non-volatile double-checked singleton and identify the visibility/publication issue. Repair it or replace it with a holder-based implementation.

### Exercise D — Diagnose an invariant

A transfer debits one account and credits another. Explain why two individually thread-safe account updates do not automatically make the transfer atomic. Propose lock ordering or a transactional boundary and discuss deadlock prevention.

## 13. Senior-level follow-up questions

1. Explain the difference between happens-before and wall-clock order.
2. What does a volatile write/read pair guarantee, and what does it not guarantee?
3. Why can `count++` lose updates even when `count` is volatile?
4. How do monitor release/acquire semantics support visibility?
5. What are safe-publication channels in the Java platform?
6. What is constructor escape, and how can it invalidate initialization assumptions?
7. Explain final-field semantics and their limits.
8. How would you investigate a rare stale-read issue in production?
9. When would LongAdder be preferable to AtomicLong, and what consistency trade-off follows?
10. How would you define a locking strategy for a multi-object business invariant?

## References for reviewer verification

- Java Language Specification, Chapter 17 (Threads and Locks): https://docs.oracle.com/javase/specs/jls/se17/html/jls-17.html
- Java API documentation for `java.util.concurrent`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/package-summary.html
- Java API documentation for `java.util.concurrent.atomic`: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/atomic/package-summary.html

**Review checklist:** verify terminology against JLS §17; run code snippets as standalone tests where applicable; confirm version labels; add quiz items only after technical review.