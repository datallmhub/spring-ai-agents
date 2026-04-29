# Recipe — Circuit breaker with Resilience4j

A circuit breaker fails fast when an upstream is unhealthy instead of burning
retries and tying up threads. In `spring-agent-flow` the graph module defines a
provider-agnostic `CircuitBreakerPolicy` SPI; the
`spring-agent-flow-resilience4j` module ships an adapter backed by
[Resilience4j](https://resilience4j.readme.io/).

## 1. Module

```xml
<dependency>
    <groupId>com.github.datallmhub.spring-agent-flow</groupId>
    <artifactId>spring-agent-flow-resilience4j</artifactId>
    <version>v0.5.0</version>
</dependency>
```

This pulls `io.github.resilience4j:resilience4j-circuitbreaker`. Nothing is
auto-configured — you build the breaker and wire it into the graph yourself.

## 2. Configure the breaker

Pick thresholds that match the upstream's real behavior. A chatty provider
with a 2 % baseline error rate should tolerate more than one with a 0.1 %
baseline.

```java
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

CircuitBreakerConfig config = CircuitBreakerConfig.custom()
        .failureRateThreshold(50.0f)              // trip when >50% of calls fail
        .slidingWindowSize(20)                    // across the last 20 calls
        .minimumNumberOfCalls(10)                 // ignore windows with <10 samples
        .waitDurationInOpenState(Duration.ofSeconds(30))
        .permittedNumberOfCallsInHalfOpenState(3) // probe calls after cooldown
        .build();

CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
```

## 3. Registry mode vs shared breaker

### Registry mode (default) — one breaker per node

The registry lazily creates (or resolves) a `CircuitBreaker` named after the
node. Use this when nodes call **different** upstreams that should trip
independently.

```java
import io.github.asekka.springai.agents.resilience4j.Resilience4jCircuitBreakerPolicy;

CircuitBreakerPolicy perNode = new Resilience4jCircuitBreakerPolicy(registry);

AgentGraph graph = AgentGraph.builder()
        .addNode("search",  searchAgent,  RetryPolicy.exponential(3, Duration.ofMillis(200)), perNode)
        .addNode("rerank",  rerankAgent,  RetryPolicy.exponential(3, Duration.ofMillis(200)), perNode)
        .addEdge("search", "rerank")
        .build();
```

`search` and `rerank` now trip independently — a Cohere outage opens the
`rerank` breaker without affecting `search`.

### Shared breaker mode — one breaker across nodes

When several nodes hit the **same** upstream, share a single breaker so failures
aggregate.

```java
CircuitBreaker sharedLlm = CircuitBreaker.of("openai", config);
CircuitBreakerPolicy shared = new Resilience4jCircuitBreakerPolicy(sharedLlm);

AgentGraph graph = AgentGraph.builder()
        .addNode("draft",   draftAgent,   null, shared)
        .addNode("critic",  criticAgent,  null, shared)
        .addNode("revise",  reviseAgent,  null, shared)
        .build();
```

Pass `null` as the retry policy to fall back to the graph-wide default.

## 4. Retry and circuit breaker together

The graph executes `breaker.execute(() -> node.run(ctx))` **inside** the retry
loop, so every retry attempt is recorded by the breaker — tripping it faster
and avoiding pointless backoff once it is open.

When the breaker is open, Resilience4j throws `CallNotPermittedException`. The
graph treats this like any other node failure, which means your
`ErrorPolicy` decides what happens next:

- `FAIL_FAST` — the run ends with a failed `AgentResult`.
- `SKIP_NODE` — the graph logs the failure and continues to the next node.
- `RETRY_ONCE` / `RetryPolicy` — the retry predicate decides whether to re-enter
  the breaker. The default predicate matches `IOException` / `TimeoutException`
  and will **not** retry on `CallNotPermittedException`, so an open breaker
  stops burning attempts.

```java
AgentGraph graph = AgentGraph.builder()
        .errorPolicy(ErrorPolicy.SKIP_NODE)
        .addNode("enrichment",
                 enrichmentAgent,
                 RetryPolicy.exponential(3, Duration.ofMillis(200)),
                 new Resilience4jCircuitBreakerPolicy(registry))
        .addEdge("enrichment", "respond")
        .build();
```

If the enrichment upstream goes down the breaker opens, the node is skipped,
and the graph still produces a (degraded) response from `respond`.

## 5. Observability

Resilience4j publishes state transitions and metrics through its own event
bus. Hook a listener once at startup to surface trips in logs:

```java
registry.getEventPublisher().onEntryAdded(event ->
    event.getAddedEntry().getEventPublisher()
            .onStateTransition(e -> log.warn("[cb] {} {} -> {}",
                    e.getCircuitBreakerName(), e.getStateTransition().getFromState(),
                    e.getStateTransition().getToState())));
```

If you already have `MicrometerAgentListener` wired (via the starter's
`MeterRegistry`), also enable the Resilience4j Micrometer bridge so breaker
state joins `agents.execution.count` on the same dashboard:

```xml
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-micrometer</artifactId>
</dependency>
```

## 6. When not to use a breaker

- The node is a **pure local computation** — retries and breakers only make
  sense around I/O boundaries.
- The upstream is a **fire-and-forget** sink (metrics, logs). Drop failures
  instead; a breaker adds state without protecting anything.
- You only have one node hitting a backend and already use `RetryPolicy` with
  a tight `maxAttempts`. The breaker pays off when multiple callers can
  collectively hammer a failing service.
