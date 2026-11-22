# Advanced Java: JVM Internals, Garbage Collection and Performance Diagnosis

> **Editorial status:** Draft for technical review; not published.
> **Audience:** Senior Java, Lead, Staff/Principal and platform interviews
> **Version scope:** Java 17 baseline; collector defaults and flags vary by JDK/vendor. Verify on the target runtime.
> **Prerequisites:** Java object model, concurrency, basic Linux and performance metrics.

## Learning outcomes

- Trace source code through compilation, class loading, interpretation and JIT compilation.
- Explain JVM runtime memory areas and distinguish Java heap from native/process memory.
- Reason about object allocation, reachability, garbage collection and latency trade-offs.
- Diagnose memory leaks, high allocation, GC pressure, thread contention and CPU saturation.
- Select evidence-driven JVM tuning and avoid cargo-cult flags.

## Interview answer

The JVM executes bytecode using a combination of interpretation and JIT compilation. Class loading follows loading, linking (verification, preparation and resolution) and initialization. Runtime memory includes the heap, per-thread stacks, metaspace, code cache and native allocations; therefore a process can exceed its container limit even when heap usage looks healthy. Garbage collection reclaims unreachable objects, but high allocation, retained object graphs, pauses or native-memory growth can still cause latency and failures. I diagnose from symptoms and telemetry—GC logs, heap dumps, thread dumps, JFR and OS/container metrics—then identify the dominant cost before changing flags. Tuning is workload- and JDK-specific and must be validated under representative load.

## 1. Execution lifecycle

1. Java source is compiled to class files containing bytecode and metadata.
2. The class loader subsystem locates and defines classes.
3. The JVM verifies bytecode and links classes.
4. Initially, code may be interpreted; hot methods/loops can be compiled by the JIT.
5. Runtime profiling guides optimization; assumptions can be invalidated and code deoptimized.
6. The garbage collector manages eligible heap objects; native resources still require correct lifecycle management.

### Class loading and initialization

Class loading is commonly described through the loading, linking and initialization phases. Linking includes verification, preparation and resolution (resolution may be lazy). Initialization executes static initializers and static field initializers under JVM class-initialization rules.

The parent-delegation model is common, but class-loader hierarchies and delegation can be customized. Class identity is determined by the binary name **and defining class loader**. Two classes with the same name loaded by different loaders are distinct types, a key cause of plugin/container ClassCastException and classloader leaks.

### Bytecode, interpreter and JIT

Bytecode is the JVM's instruction set, not machine code. The execution engine may interpret bytecode and compile hot code to native instructions. Tiered compilation balances startup and peak performance in many HotSpot configurations.

JIT optimizations may include inlining, escape analysis and scalar replacement, but these are implementation-dependent. Do not promise that a particular object is always stack-allocated: Java's observable semantics and JIT decisions are more nuanced.

Use `javap -c -v` to inspect bytecode and JFR/JIT diagnostics to understand runtime behavior rather than inferring it from source alone.

## 2. Runtime memory areas

| Area | Purpose | Diagnostic clue |
|---|---|---|
| Heap | Objects and arrays; shared across threads | Heap pressure, GC, retained graph |
| Java thread stacks | Frames, local variables, operand stacks | StackOverflowError, thread count/native memory |
| Metaspace | Class metadata, native memory | Classloader leak, metadata growth |
| Code cache | JIT-compiled native code | Compilation/code-cache diagnostics |
| Direct/native memory | Native libraries, direct buffers, runtime allocations | RSS high while heap stable |
| GC structures | Collector-specific metadata and regions | Collector overhead and tuning |

Each thread has a stack. A large number of threads can consume substantial native memory even when heap usage is modest. Heap maximum is not a process memory limit.

### Object layout and allocation

Object size depends on the VM, architecture, alignment, headers, field layout and compressed-reference settings. Avoid hard-coded byte sizes unless measured on a stated runtime.

Most objects are allocated in a thread-local allocation buffer when possible; allocation is often cheap, but high allocation rate can create GC pressure. Reducing allocation may improve throughput and latency, but object pooling can introduce contention, retention and complexity. Profile before pooling.

## 3. Reachability and garbage collection

GC determines whether objects remain reachable from roots such as thread stacks, static references and JNI references. An object is not collectible merely because application code no longer “needs” it; a retained reference path keeps it reachable.

Common retention roots:
- Long-lived static maps or caches without eviction.
- Listener/callback registries that never unregister.
- ThreadLocal values attached to pooled threads.
- Unbounded queues and collections.
- Class loaders retained by static references or threads.
- Closures retaining large enclosing objects.

### Reference types and finalization

Strong references keep objects reachable. Soft, weak and phantom references have specialized semantics and should not be used as a substitute for an explicit cache/resource design. Finalization is deprecated for removal and is not a reliable resource-management strategy. Use try-with-resources and explicit close semantics for files, sockets and other native resources.

## 4. Collector concepts and selection

Collectors trade throughput, footprint and pause behavior. Choice depends on JDK version, vendor, heap size, allocation profile, latency objectives and operational constraints.

- **Serial GC:** simple, often suitable for small heaps or constrained environments; stop-the-world collection.
- **Parallel GC:** emphasizes throughput using parallel collection work; pauses can be longer.
- **G1:** region-based collector designed to balance throughput and pause-time goals; a common server choice in modern HotSpot.
- **ZGC / Shenandoah:** low-pause-oriented collectors with different implementation and version characteristics; verify availability, maturity and operational support for the deployed JDK.

Do not claim one collector is universally fastest. Compare under realistic traffic, heap sizing and pause/throughput SLOs.

### GC event interpretation

Track:
- Allocation rate and promotion/tenuring behavior.
- Pause duration distribution, not only average.
- Frequency and cause of collections.
- Heap occupancy before and after collection.
- Concurrent-cycle behavior and CPU overhead.
- Application latency during collection.

A high post-GC live set may indicate a large working set or retention. A high allocation rate with a stable live set may indicate churn rather than a leak.

## 5. Common memory failures

### Java heap OutOfMemoryError

Possible causes: live set exceeds available heap, leak, unbounded growth, large batch, cache without limits or insufficient heap for workload. A heap dump can reveal dominators and retained-size paths.

### Metaspace OutOfMemoryError

Often linked to excessive class generation or classloader retention, particularly in reloadable/plugin environments. Inspect class loading/unloading and classloader references.

### Direct buffer/native memory failure

Heap may remain stable while direct buffers, JNI or native libraries consume memory. Compare JVM memory counters with process RSS and container metrics; use Native Memory Tracking when enabled and supported.

### StackOverflowError

Often caused by uncontrolled recursion or deep call chains. Thread stack size affects the boundary but should not be used to mask a recursion defect.

## 6. Diagnostic workflow: symptom to evidence

### A. Establish the symptom

- Is it CPU saturation, latency, throughput loss, memory growth, GC pauses, blocked threads or crash?
- Compare application metrics, JVM metrics, host/container CPU and memory, and deployment changes.
- Define the time window and correlate with traffic, releases and dependencies.

### B. Capture the right evidence

| Symptom | Evidence |
|---|---|
| CPU high | CPU profile/JFR, process and thread CPU, hot methods |
| Heap grows | Heap dump, GC logs, retained-size/dominator tree |
| RSS grows but heap stable | Native Memory Tracking, direct-buffer metrics, OS/container data |
| Requests stall | Thread dump, lock/park states, pool and queue metrics |
| GC latency | GC logs/JFR, pause distribution, allocation and live-set trends |
| Startup/class issues | Class loading logs, dependency/classloader inspection |

### C. Useful commands (verify permissions and target PID)

```bash
jcmd <pid> VM.version
jcmd <pid> VM.flags
jcmd <pid> GC.heap_info
jcmd <pid> GC.class_histogram
jcmd <pid> Thread.print
jcmd <pid> JFR.start name=incident settings=profile duration=60s filename=incident.jfr
```

Heap dump example:

```bash
jcmd <pid> GC.heap_dump /secure/path/heap.hprof
```

Heap dumps and recordings can contain credentials, tokens, personal data and business payloads. Store securely, restrict access and follow retention policy. A heap dump can pause or stress a production process; assess impact before capture.

## 7. JFR, profiling and measurement

Java Flight Recorder can collect low-overhead event data when configured appropriately, but overhead depends on event settings and workload. Use recordings to inspect allocation, GC, thread activity, locks, I/O and execution profiles.

A disciplined investigation:
1. Form a hypothesis from metrics.
2. Capture a bounded recording during the relevant workload.
3. Identify the dominant stack or allocation site.
4. Make one controlled change.
5. Re-run the same workload and compare p50/p95/p99, throughput, CPU and memory.
6. Keep or revert based on evidence.

Avoid microbenchmarks that ignore warm-up, dead-code elimination, runtime compilation and measurement noise. For JVM microbenchmarking, use a purpose-built harness such as JMH and understand its setup.

## 8. Container-aware runtime

In containers, verify the JVM's awareness of cgroup limits and the effective heap/CPU settings for the actual JDK and vendor. A pod's memory limit must account for heap, metaspace, stacks, direct buffers, native libraries and safety headroom.

Operational checklist:
- Confirm effective JVM flags and ergonomics at startup.
- Align heap maximum with container memory budget rather than setting it equal to the entire limit.
- Monitor RSS/container working set in addition to heap.
- Set realistic CPU requests/limits and evaluate throttling.
- Check thread count and pool sizes under load.
- Validate graceful shutdown and dump/recording paths.

## 9. Performance tuning: order of operations

1. Verify correctness and reproduce the workload.
2. Identify bottleneck class: CPU, allocation/GC, database, network, lock or queue.
3. Improve algorithm/data structure or eliminate unnecessary work.
4. Reduce avoidable allocations and payload sizes where measured.
5. Tune pool concurrency and queue limits; avoid unbounded queues.
6. Adjust heap/collector only with evidence.
7. Re-test under load and check tail latency plus resource cost.

A larger heap may reduce collection frequency but increase footprint and possibly pause/working-set effects. A smaller heap may increase collection pressure. Choose based on observed behavior, not a slogan.

## 10. Senior interview case studies

### Case A — Heap stable, container OOMKilled

Investigate RSS, direct buffers, thread count/stacks, metaspace, native libraries and cgroup limit. Do not immediately increase `-Xmx`; it may worsen the failure.

### Case B — Frequent GC, low post-GC occupancy

Likely allocation churn. Profile allocation sites, temporary objects, serialization, collection copying and request volume. Consider batching or reducing intermediate allocations after measuring.

### Case C — Memory rises over days

Compare heap dumps at different times; identify dominator paths and retained objects. Examine cache eviction, listener lifecycle, ThreadLocal cleanup and classloader retention.

### Case D — p99 spikes but average latency is fine

Correlate tail latency with GC pauses, CPU throttling, lock contention, queue saturation, slow dependencies and connection pool exhaustion. Averages hide the failure mode.

## 11. Exercises

1. Explain why a process can be OOMKilled with heap below `-Xmx`.
2. Use a heap dump to identify the top retained object path; document why it is retained and propose a bounded lifecycle.
3. Compare two collectors using the same workload and report throughput, p99 latency, pause distribution and memory.
4. Capture a thread dump from a deliberately blocked executor and identify queue/worker starvation.
5. Design a production JVM dashboard: heap/live set, allocation, GC pause, CPU, thread count, pool saturation, RSS and request latency.

## 12. Interview follow-ups

- What happens between class loading and initialization?
- How is class identity determined?
- What does JIT compilation optimize, and why can deoptimization occur?
- Heap vs stack vs metaspace vs native memory?
- Why is “object allocated on stack” an unsafe blanket statement?
- How do you distinguish a memory leak from allocation pressure?
- How would you investigate high RSS with low heap?
- Compare G1 and low-pause collectors at the level of goals and trade-offs.
- What information does a heap dump provide that GC logs do not?
- How do you tune JVM memory for Kubernetes without risking OOMKilled?
- Why are p99 and saturation metrics more actionable than averages?
- What makes a benchmark invalid?

## Reviewer references

- Java Virtual Machine Specification: https://docs.oracle.com/javase/specs/jvms/se17/html/
- JDK 17 troubleshooting guide: https://docs.oracle.com/en/java/javase/17/troubleshoot/
- JDK 17 JFR documentation: https://docs.oracle.com/en/java/javase/17/jfapi/
- JDK 17 `jcmd` documentation: https://docs.oracle.com/en/java/javase/17/docs/specs/man/jcmd.html
- Garbage collection tuning guide (JDK 17): https://docs.oracle.com/en/java/javase/17/gctuning/

**Editorial gate:** Validate commands against the supported runtime, review collector/version claims, ensure sensitive-data warnings are retained, and do not publish until technical review.