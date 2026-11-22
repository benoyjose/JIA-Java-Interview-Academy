# Advanced Java: Collections, Generics and Stream Pipelines

> **Editorial status:** Draft for technical review; not published.
> **Audience:** Senior Java Engineer through Staff/Principal
> **Baseline:** Java 17; features from later releases are identified separately.
> **Prerequisites:** Java object model, interfaces, basic lambda syntax and Big-O.

## Learning outcomes

By the end, you can choose a collection from its behavioral contract and workload; explain HashMap's implementation and performance boundaries; reason about equality, ordering and iterator behavior; design type-safe generic APIs; explain erasure and bridge methods; build correct stream pipelines; and identify when a parallel stream is harmful.

## Interview answer

I select a collection by its contract, complexity, ordering, concurrency semantics and memory profile—not by habit. Lists preserve positional order, sets enforce uniqueness under their equality or comparator contract, and maps model key-value lookup. Hash-based structures depend on stable equals/hashCode behavior; sorted structures depend on a consistent comparator. Generics provide compile-time type safety, but Java implements them primarily through erasure, so runtime checks and overload rules have limitations. Streams describe a lazy computation over a source; intermediate operations generally execute only when a terminal operation consumes the pipeline. Parallelism is not automatically faster and requires suitable splitting, stateless operations and a safe reduction.

## 1. Collection contracts and selection

The Collection Framework separates interfaces from implementations. Code to interfaces where possible, but choose the concrete implementation deliberately.

| Need | Typical choice | Contract / caveat |
|---|---|---|
| Indexed access and append-heavy list | ArrayList | O(1) amortized append and indexed read; middle insert/remove shifts |
| Frequent insert/remove at known node with traversal already available | LinkedList | O(1) link change after locating node; indexed access is linear; high object overhead |
| Unique elements, no required iteration order | HashSet | Average constant-time operations with good hashes; no ordering promise |
| Unique insertion order | LinkedHashSet | Maintains insertion order with extra linkage |
| Sorted keys/range queries | TreeMap / TreeSet | O(log n); comparator defines key equivalence |
| FIFO/deque | ArrayDeque | Resizable array; usually preferred over legacy Stack |
| Priority retrieval | PriorityQueue | Head is least according to ordering; iteration is not sorted |
| Shared high-concurrency map | ConcurrentHashMap | Per-operation concurrency guarantees; compound workflows need care |
| Producer-consumer coordination | BlockingQueue implementation | Explicit capacity, blocking and shutdown policy |

### Complexity is conditional

Hash structures commonly offer expected O(1) lookup, not a universal worst-case guarantee. Complexity depends on hash distribution, resizing, implementation details and workload. Tree structures provide logarithmic operations but impose comparison costs and ordering semantics. Benchmark with realistic key distributions and access patterns.

### ArrayList growth

ArrayList stores elements in a backing array. When capacity is exhausted, it allocates a larger array and copies elements. An individual append can therefore be O(n), while append across a sequence is amortized O(1). Pre-sizing can reduce reallocations when a credible size estimate exists; excessive capacity wastes memory.

### HashMap internals (OpenJDK implementation detail)

At a high level, HashMap uses a bucket array. A key's hash is spread and mapped to a bucket index. Collisions are handled within a bucket; in modern OpenJDK implementations, sufficiently populated buckets may be treeified under capacity and threshold conditions.

Important interview distinctions:

- Capacity is bucket-array size; size is number of mappings.
- Load factor influences resize threshold, not a guarantee of uniform hashing.
- Resizing redistributes entries according to the new capacity.
- A null key is supported by HashMap; ConcurrentHashMap does not permit null keys or values.
- HashMap is not thread-safe. Concurrent structural mutation without external coordination is invalid.
- Internal thresholds and treeification constants are implementation details; describe them as OpenJDK behavior, not a language-level contract.

### equals/hashCode contract

If two objects are equal according to `equals`, they must return the same `hashCode`. Unequal objects may share a hash. Equality should be reflexive, symmetric, transitive, consistent and false for null.

A key's equality-relevant state should not change while it is stored in a hash-based collection. If its hash changes, lookup may search a different bucket and fail to find the stored entry.

```java
record EmployeeKey(String tenant, long employeeId) {}
Map<EmployeeKey, String> directory = new HashMap<>();
directory.put(new EmployeeKey("acme", 42), "Ben");
String result = directory.get(new EmployeeKey("acme", 42)); // found
```

Records provide value-based equals/hashCode for their components, which makes them useful immutable keys when component values are themselves stable.

### Sorted collections and Comparator

TreeMap and TreeSet treat keys as equivalent when the comparator returns zero. If comparator equivalence differs from equals, the collection's behavior can surprise callers and may violate expectations of the general Map/Set contract.

```java
Comparator<String> byLength = Comparator.comparingInt(String::length);
Set<String> values = new TreeSet<>(byLength);
values.add("cat");
values.add("dog"); // comparator returns 0: only one element remains
```

A comparator must satisfy its ordering contract. Avoid subtraction-based comparisons that can overflow; use `Integer.compare`, `Long.compare` or comparator combinators.

## 2. Iteration, mutation and concurrency

### Fail-fast is bug detection, not synchronization

Many general-purpose collection iterators are fail-fast and may throw ConcurrentModificationException after structural modification outside the iterator's supported mutation path. This is best-effort behavior; it is not a correctness mechanism and does not make the collection thread-safe.

Use iterator.remove when supported, or collect changes separately. For concurrent access, choose a concurrent collection or guard the entire access protocol with a lock.

### ConcurrentHashMap

ConcurrentHashMap supports concurrent retrieval and updates and provides per-operation memory-consistency guarantees. It does not make a multi-step business operation atomic by default.

```java
// A check-then-act race:
if (!map.containsKey(key)) {
    map.put(key, createValue());
}

// Atomic mapping operation for this key:
map.computeIfAbsent(key, ignored -> createValue());
```

The mapping function should be short, deterministic enough for the contract, and should not recursively modify the same map in unsafe ways. For multiple keys or external side effects, use a wider transaction/locking strategy.

## 3. Generics: type safety and API design

Generics move many errors from runtime to compile time:

```java
List<String> names = new ArrayList<>();
names.add("Ada");
// names.add(7); // compile-time error
String first = names.get(0);
```

They are invariant: `List<Integer>` is not a subtype of `List<Number>`. If it were, code could add a Double to a list intended to contain only Integers.

### Upper and lower bounded wildcards

- `? extends T`: producer of T values; safely read as T, but cannot add arbitrary T (except null).
- `? super T`: consumer of T values; can add T, but reads are only safely typed as Object.
- Unbounded `?`: unknown type; useful for operations independent of the element type.

PECS—Producer Extends, Consumer Super—is a practical API-design heuristic, not a substitute for understanding the actual variance requirements.

```java
static double sum(List<? extends Number> numbers) {
    double total = 0;
    for (Number n : numbers) total += n.doubleValue();
    return total;
}

static <T> void copy(List<? extends T> source,
                      List<? super T> destination) {
    for (T item : source) destination.add(item);
}
```

### Generic methods and inference

A generic method declares type variables independently of a class:

```java
static <T> T first(List<T> values) {
    if (values.isEmpty()) throw new NoSuchElementException();
    return values.get(0);
}
```

Type inference uses invocation context and constraints. When inference becomes difficult, introduce an explicit type witness or a named intermediate variable to improve readability.

## 4. Type erasure and runtime limitations

Java's generics are mostly implemented using erasure: type variables are replaced by their bounds (or Object), and compiler-generated casts preserve source-level type safety.

Consequences:

- `new T()` is not generally possible because T's runtime class is not known.
- `List<String>.class` is illegal; `List.class` exists but does not retain String as a runtime parameter.
- You cannot overload methods solely by generic parameterization if their erased signatures collide.
- Generic arrays are restricted because arrays are reified and covariant, while generic parameterization is erased.
- Reflection can inspect some generic declaration metadata, but that is not the same as each object carrying its concrete type arguments.

### Bridge methods

When a generic method or covariant override erases to a signature that needs to preserve polymorphism, the compiler may generate a synthetic bridge method. This is an implementation mechanism; inspect bytecode with `javap -c -v` when debugging unusual dispatch or reflection output.

## 5. Streams: pipeline model

A stream is not a collection and does not store data. It describes a traversal and transformation pipeline.

- **Source:** collection, array, generator, I/O source, etc.
- **Intermediate operations:** map, filter, flatMap, sorted, distinct, peek; generally lazy.
- **Terminal operation:** collect, reduce, forEach, count, findFirst, anyMatch; triggers evaluation and consumes the stream.

A stream should normally be used once. Reusing a consumed stream throws IllegalStateException.

```java
Map<String, Long> countByDepartment =
    employees.stream()
        .filter(Employee::active)
        .collect(Collectors.groupingBy(
            Employee::department,
            Collectors.counting()));
```

### map vs flatMap

`map` transforms one input to one output. `flatMap` transforms each input into a stream/sequence and flattens the nested result.

```java
List<String> skills =
    employees.stream()
        .flatMap(e -> e.skills().stream())
        .distinct()
        .sorted()
        .toList(); // Java 16+
```

For Java 8–15, use an appropriate collector such as `collect(Collectors.toList())`; do not present `Stream.toList()` as a Java 8 API.

### Reduction and mutable reduction

Use `reduce` for associative reduction to a value; use `collect` for mutable accumulation such as a list, map or grouping result. A reduction operator should be associative for parallel correctness, and identity/combiner contracts must be respected.

Avoid shared mutable state in stream callbacks:

```java
List<Integer> unsafe = new ArrayList<>();
values.parallelStream().forEach(unsafe::add); // data race

List<Integer> safe =
    values.parallelStream().collect(Collectors.toList());
```

The collector provides an accumulation strategy that can be combined correctly.

### Stateful operations and side effects

Stream behavioral parameters should generally be stateless and non-interfering. Side effects inside map/filter/peek can be skipped, reordered or executed concurrently depending on pipeline and terminal operation. Use streams to express transformations, not to hide imperative workflows with external mutation.

## 6. Parallel streams: decision framework

Parallel streams split work through the stream's execution machinery and combine partial results. They can help when:

- The source splits efficiently (often arrays or ArrayList).
- The workload is CPU-bound and sufficiently large.
- Operations are stateless, non-blocking and have low coordination cost.
- Reduction is associative and the result contract supports parallel combination.

They may hurt when:

- The dataset is small or splitting/combining costs dominate.
- The source is poorly splittable.
- Work blocks on network/database calls.
- Operations contend on locks or shared state.
- Ordering requirements constrain execution.
- The common ForkJoinPool is already saturated by unrelated work.

Do not use parallelStream as a generic “make it faster” switch. Measure throughput and tail latency in a representative environment, and understand the pool and blocking behavior.

## 7. Common pitfalls

1. Mutable object used as a HashMap key.
2. equals implemented without a compatible hashCode.
3. Comparator returns zero for distinct values that the application expects to retain.
4. Assuming LinkedList is faster for all inserts/removes (locating the position is often O(n)).
5. Treating ConcurrentModificationException as a thread-safety guarantee.
6. Assuming ConcurrentHashMap makes multi-key updates atomic.
7. Confusing `? extends` and `? super` and using raw types to silence compiler errors.
8. Assuming generic type arguments are available at runtime.
9. Reusing a stream or placing side effects in intermediate operations.
10. Using parallel streams for blocking I/O or non-associative reduction.
11. Using `peek` as a guaranteed debugging or business side-effect hook.
12. Returning an unmodifiable stream result and then attempting mutation (`Stream.toList()`).

## 8. Production-oriented examples

### Index by key with duplicate policy

```java
Map<Long, Employee> byId = employees.stream()
    .collect(Collectors.toMap(
        Employee::id,
        Function.identity(),
        (existing, replacement) -> existing));
```

The merge function explicitly defines duplicate-key behavior. Without it, duplicate keys cause an exception.

### Group and summarize

```java
Map<String, Double> averageSalaryByTeam =
    employees.stream()
        .collect(Collectors.groupingBy(
            Employee::team,
            Collectors.averagingDouble(Employee::salary)));
```

For very large data, consider whether database aggregation is more efficient than loading every row into the JVM.

### Top-N without sorting the entire dataset

A bounded heap can be more efficient than sorting all N values when only a small top K is needed. Choose the comparator carefully and validate the boundary case K <= 0. For production-scale use, benchmark against sorting and database-side ORDER BY/LIMIT.

## 9. Hands-on exercises

### Exercise 1 — Key mutation
Create a mutable key whose hash depends on a field. Insert it into HashMap, mutate the field and attempt lookup. Explain the result and repair the model.

### Exercise 2 — Collection choice
Design a product inventory index supporting exact SKU lookup, insertion order for recent additions, sorted range queries by price and concurrent reads. Identify whether one structure or multiple indexes best fit and explain consistency cost.

### Exercise 3 — Generic copy
Implement a type-safe copy method that copies from a source of subtype values to a destination accepting the supertype. Explain the wildcard bounds.

### Exercise 4 — Stream correctness
Rewrite a pipeline that mutates a shared list in parallel. Then explain whether the output ordering is guaranteed and whether the result needs to be mutable.

### Exercise 5 — Performance review
Given a pipeline that filters, sorts, groups and counts millions of database-backed records, identify which work could be pushed to SQL, which operations require memory, and how you would benchmark.

## 10. Interview questions and expected points

1. Why is List<Integer> not assignable to List<Number>? — Invariance prevents unsafe writes.
2. Explain PECS. — Producer extends, consumer super; justify from read/write operations.
3. What is type erasure? — Runtime representation, casts, signature restrictions, bridge methods.
4. How does HashMap resolve collisions? — Bucket-based implementation; collision handling and OpenJDK treeification qualifications.
5. What happens when a key's hash changes after insertion? — Lookup may use a different bucket; key contract violated.
6. HashMap vs ConcurrentHashMap? — Thread-safety, null policy, concurrent operations, compound workflow caveat.
7. Why is LinkedList indexed access expensive? — Traversal to node is linear.
8. What is the difference between map and flatMap? — One-to-one transformation vs flattening nested sequences.
9. Why can parallelStream be slower? — Overhead, splitting, contention, blocking, common-pool pressure.
10. Is Stream.toList() mutable? — It returns an unmodifiable list; API introduced in Java 16.
11. When should SQL perform grouping instead of Java streams? — Data locality, transfer cost, DB execution plan and resource boundaries.
12. How do you implement duplicate handling in toMap? — Supply merge function and define domain policy.

## Reviewer references

- Java Collections Framework: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/Collection.html
- HashMap API contract: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/HashMap.html
- Generics tutorial: https://docs.oracle.com/javase/tutorial/java/generics/
- Stream API: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/package-summary.html
- Stream behavioral parameters: https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/stream/package-summary.html

**Editorial gate:** Verify API-version claims, compile snippets in a dedicated fixture, check examples against JDK 17, and obtain technical review before importing or publishing.