# spring-agent-flow

**Stateful multi-agent orchestration for Spring AI.**
Design and run long-lived agent workflows with state, retries, and graph execution — all in Java, without manual orchestration code.

[![build](https://github.com/datallmhub/spring-agent-flow/actions/workflows/build.yml/badge.svg)](https://github.com/datallmhub/spring-agent-flow/actions)
[![Java 17+](https://img.shields.io/badge/Java-17%2B-blue)](https://adoptium.net/)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-1.0-green)](https://docs.spring.io/spring-ai/reference/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

> Independent project — not affiliated with [`spring-ai-community/spring-ai-agents`](https://github.com/spring-ai-community/spring-ai-agents), which is the official AgentClient abstraction over CLI agents (Claude Code, Codex, Gemini, etc.). This repository has a different scope: a graph-based runtime for stateful agent **workflows** on top of Spring AI.

---

## What problem does this solve?

Real-world AI systems quickly move past a single `ChatClient` call:

- multi-step pipelines (research → analyse → draft → review)
- multiple agents coordinating on one task
- transient errors, rate limits, partial failures
- state that must survive a crash or wait on human input

You end up writing the orchestration, the retries, the resume logic. This
project replaces that scaffolding with a structured runtime — an
`AgentGraph` for explicit flow, `RetryPolicy` and `CircuitBreakerPolicy`
for resilience, durable checkpoints for resume.

### When should I use this?

Use it when **at least one** of these is true:

- the agent needs more than one LLM call to finish the job
- the workflow has branches, loops, or human-in-the-loop pauses
- failure recovery (retry, breaker, resume after crash) is in scope
- multiple tools or agents must coordinate around shared, typed state

**Don't** use it for a single LLM call with a stateless prompt — Spring AI's
`ChatClient` already covers that cleanly.

---

## 60 seconds to multi-agent

```java
// 1. Define specialized agents
ExecutorAgent researcher = ExecutorAgent.builder()
        .chatClient(chatClient)
        .systemPrompt("You are a research specialist. Find key facts.")
        .tools(searchTool, wikipediaTool)
        .build();

ExecutorAgent writer = ExecutorAgent.builder()
        .chatClient(chatClient)
        .systemPrompt("Write a clear report from the research findings.")
        .build();

// 2. Let a coordinator route dynamically
CoordinatorAgent coordinator = CoordinatorAgent.builder()
        .executors(Map.of("research", researcher, "writing", writer))
        .routingStrategy(RoutingStrategy.llmDriven(chatClient))
        .build();

// 3. Run
AgentResult result = coordinator.execute(
        AgentContext.of("Compare Claude 4 and GPT-5 for enterprise use"));

System.out.println(result.text());
```

No `while` loops. No manual routing. Just agents that collaborate.

---

## Try it now — no LLM needed

Clone and run any of the 3 examples. They use simulated agents, no API key required.

```bash
git clone https://github.com/datallmhub/spring-agent-flow.git
cd spring-agent-flow
mvn install -DskipTests -q
mvn -pl spring-agent-flow-samples exec:java     # runs MultiAgentCoordination by default
```

Output:

```
=== Multi-Agent Coordination ===

Request: "Research the latest advances in quantum computing"
[router]   Routing to: research
[research] Searching for: quantum computing

Result: Found 3 papers on quantum error correction (2026).
        Key finding: logical qubit fidelity reached 99.8%.

---

Request: "Write a blog post about the findings"
[router]   Routing to: writing
[writing]  Generating content...

Result: # Quantum Computing in 2026
        Logical qubit fidelity has reached 99.8%, marking a milestone...
```

**All 3 samples:**

| Example | What it shows | Run with |
|---------|--------------|----------|
| `MultiAgentCoordination` (default) | Squad API + routing | `mvn -pl spring-agent-flow-samples exec:java` |
| `MinimalPipeline` | Two-step graph | `-Dexec.mainClass="...samples.MinimalPipeline"` |
| `AdvancedGraphDemo` | Conditional loops, state, listener | `-Dexec.mainClass="...samples.AdvancedGraphDemo"` |

---

## Working with real LLMs (Safety & Constraints)

While this framework handles complex logic routing, executing graphs against live APIs (Mistral, OpenAI, Anthropic) requires managing their physical limitations. `spring-agent-flow` incorporates safety features to counteract these common pitfalls:

**1. Strict Message Alternation (Safe Defaults)**
Some providers (like Mistral) will crash with `HTTP 400 Bad Request` if your prompt ends with an `AssistantMessage` or contains consecutive identical roles. During node chaining (A → B → C), `ExecutorAgent` uses built-in **Safe Message Ordering** to transparently interleave a padding `UserMessage` when required to prevent structure violations.

**2. Rate Limiting & Transient Errors**
Agentic graphs generate requests instantly. A 3-step pipeline fires 3 requests in under 2 seconds. Free-tier architectures (typically 1 request/sec) will fail with `429 Too Many Requests` or `503 Service Unavailable`.
*Solution*: Your components should rely on `.errorPolicy(ErrorPolicy.RETRY_ONCE)` mapped to Spring AI's automatic Retry mechanisms, or manually interleave non-blocking delays in intensive ReAct loops.

**3. Non-deterministic Routing**
A conditional edge verifying `result.text().contains("APPROVED")` can break if the agent suddenly decides to output `"Document is approved"` instead of exact text. Always implement fuzzy matching or use LLM structured JSON output mappings for robust conditional routing rules.

---

## Why not just use Spring AI?

Spring AI is perfect for single-step interactions.

As soon as your system requires:

- multiple steps
- retries and recovery
- coordination between components

you start writing orchestration code. This project removes that layer.

| Spring AI | spring-agent-flow (built on top) |
|---|---|
| Low-level primitives (`ChatClient`, tools, advisors) | Structured agent runtime (`Agent`, `AgentGraph`, `Squad`) |
| Code-driven orchestration (flexible, unstructured) | Graph-based orchestration with explicit edges & cycles |
| No built-in durable state | Typed shared state (`StateKey<T>`) + JDBC/Redis checkpoints |
| Error handling left to application code | `RetryPolicy` (exp. backoff + jitter) and `CircuitBreakerPolicy`, per-node |
| No native interrupt/resume | First-class `interrupted(...)` + `graph.resume(runId, ...)` |
| Observability via app-level integration | Built-in Micrometer metrics through the starter |

---

## Installation

**Requirements:** Java 17+, Spring Boot 3.x, Spring AI 1.0+ (only when using `ExecutorAgent` or the starter).

Distributed via [JitPack](https://jitpack.io).

### Maven

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.datallmhub.spring-agent-flow</groupId>
    <artifactId>spring-agent-flow-starter</artifactId>
    <version>v0.5.0</version>
</dependency>
```

### Gradle

```groovy
repositories {
    maven { url 'https://jitpack.io' }
}

dependencies {
    implementation 'com.github.datallmhub.spring-agent-flow:spring-agent-flow-starter:v0.5.0'
}
```

### Modules

The starter pulls everything you typically need. Pick individual modules when
you want a smaller footprint or run outside Spring Boot:

| Module | Use case |
|---|---|
| `spring-agent-flow-starter` | Spring Boot auto-config, properties, Micrometer listener |
| `spring-agent-flow-core` | Minimal API (`Agent`, `AgentContext`, `StateKey`, `AgentResult`) |
| `spring-agent-flow-graph` | `AgentGraph`, `RetryPolicy`, `CircuitBreakerPolicy` SPI, checkpoint contract |
| `spring-agent-flow-squad` | `CoordinatorAgent`, `ExecutorAgent`, `ReActAgent`, `ParallelAgent`, `RoutingStrategy` |
| `spring-agent-flow-checkpoint` | `JdbcCheckpointStore`, `RedisCheckpointStore`, Jackson codec |
| `spring-agent-flow-resilience4j` | `CircuitBreakerPolicy` adapter backed by Resilience4j |
| `spring-agent-flow-cli-agents` | `CliAgentNode` — runs Claude Code / Codex / Gemini CLI agents as graph nodes |
| `spring-agent-flow-test` | `MockAgent`, `TestGraph` for unit-testing graphs |

The starter auto-configures everything. Minimal `application.yml`:

```yaml
spring:
  ai:
    agents:
      enabled: true
      default-error-policy: RETRY_ONCE
      observability:
        metrics: true
```

---

## Two levels of control

### Level 1 — Squad API (recommended)

The default. A `CoordinatorAgent` routes to `ExecutorAgent`s. You focus on the agents, not the plumbing.

```java
CoordinatorAgent coordinator = CoordinatorAgent.builder()
        .executors(Map.of(
            "research", researchExecutor,
            "analysis", analysisExecutor,
            "writing",  writingExecutor
        ))
        .routingStrategy(RoutingStrategy.llmDriven(chatClient))
        .build();

AgentResult result = coordinator.execute(AgentContext.of("..."));
```

### Level 2 — Graph API (when you need fine control)

For explicit sequencing, conditional branching, or cycles (ReAct loops):

```java
AgentGraph graph = AgentGraph.builder()
        .addNode("research", researcher)
        .addNode("analyze",  analyzer)
        .addNode("write",    writer)
        .addEdge("research", "analyze")
        .addEdge(Edge.conditional("analyze",
                ctx -> ctx.get(CONFIDENCE).doubleValue() < 0.7,
                "research"))                               // loop back
        .addEdge("analyze", "write")                       // fallback: forward
        .errorPolicy(ErrorPolicy.RETRY_ONCE)
        .build();

AgentResult result = graph.invoke(AgentContext.of("..."));
```

---

## Streaming

Every agent supports `Flux<AgentEvent>` out of the box:

```java
graph.invokeStream(AgentContext.of("hello"))
    .subscribe(event -> {
        switch (event) {
            case AgentEvent.Token t         -> System.out.print(t.chunk());
            case AgentEvent.NodeTransition x -> System.out.println("\n--> " + x.to());
            case AgentEvent.Completed c     -> System.out.println("\n[done]");
            default -> {}
        }
    });
```

---

## Typed state — no `Map<String, Object>`

```java
// Declare keys with types — compile-time safety
StateKey<Double> CONFIDENCE = StateKey.of("confidence", Double.class);
StateKey<String> SUMMARY    = StateKey.of("summary",    String.class);

// Use them anywhere
AgentContext ctx = context.with(CONFIDENCE, 0.85);
double score = ctx.get(CONFIDENCE);  // no cast needed
```

---

## Architecture

```
┌─────────────────────────────────────────────────┐
│                 Your Application                │
├─────────────────────────────────────────────────┤
│ spring-agent-flow-starter (auto-config, metrics) │
├──────────────┬──────────────────┬───────────────┤
│ checkpoint   │ resilience4j     │               │
│ JDBC, Redis  │ CircuitBreaker   │               │
├──────────────┴──────────────────┴───────────────┤
│   squad              │   graph                  │
│   CoordinatorAgent   │   AgentGraph (runtime)   │
│   ExecutorAgent      │   Node, Edge             │
│   RoutingStrategy    │   RetryPolicy, CB SPI    │
│   ReActAgent         │   CheckpointStore        │
│   ParallelAgent      │   ErrorPolicy            │
├──────────────────────┴──────────────────────────┤
│ spring-agent-flow-core                           │
│ Agent, AgentContext, AgentResult, AgentEvent     │
│ StateKey, StateBag                              │
├─────────────────────────────────────────────────┤
│                   Spring AI                     │
│           ChatClient, ToolCallbacks             │
└─────────────────────────────────────────────────┘
```

---

## Resilience

Three layers compose: error policy (what to do on failure), retry (how to
recover from transient failure), circuit breaker (when to stop trying).

```java
AgentGraph.builder()
    .errorPolicy(ErrorPolicy.FAIL_FAST)          // or RETRY_ONCE / SKIP_NODE
    .retryPolicy(RetryPolicy.exponential(3, Duration.ofMillis(200)))
    .addNode("llm", flakyAgent,
             RetryPolicy.exponential(5, Duration.ofMillis(500)),   // per-node override
             new Resilience4jCircuitBreakerPolicy(registry))        // per-node breaker
    .build();
```

- **`RetryPolicy`** — bounded jitter `[cap*(1-f), cap]` so retries never
  exceed `maxDelay`; per-node override wins over the graph default.
- **`CircuitBreakerPolicy`** — SPI lives in the graph module (R4j-free).
  `spring-agent-flow-resilience4j` ships an adapter backed by Resilience4j's
  `CircuitBreakerRegistry` (per-node breakers) or a shared breaker for
  aggregate counting.
- **`ErrorPolicy.SKIP_NODE`** — propagate past a blown breaker when the rest
  of the graph can still make useful progress.

See [resilient-typed-executor.md](docs/recipes/resilient-typed-executor.md)
and [circuit-breaker.md](docs/recipes/circuit-breaker.md).

---

## Observability (Micrometer)

When a `MeterRegistry` is available, the starter registers metrics automatically:

| Metric | Tags | Description |
|--------|------|-------------|
| `agents.execution.count` | `agent`, `graph`, `status` | Per-node execution count |
| `agents.execution.duration` | `agent`, `graph` | Per-node execution time |
| `agents.graph.transitions` | `graph`, `from`, `to` | Node-to-node transitions |
| `agents.execution.errors` | `agent`, `graph`, `cause` | Error count by type |

Custom instrumentation via `AgentListener`:

```java
AgentGraph.builder()
    .listener(new AgentListener() {
        @Override
        public void onNodeExit(String g, String node, AgentResult r, long ns) {
            log.info("{} completed in {}ms", node, ns / 1_000_000);
        }
    })
```

---

## Recipes

- [ReAct loop](docs/recipes/react-loop.md) — self-correcting agent with observation/action cycles
- [Supervisor pattern](docs/recipes/supervisor-pattern.md) — coordinator re-routes until done
- [Parallel executors](docs/recipes/parallel-executors.md) — fan-out/fan-in
- [Subgraphs](docs/recipes/subgraphs.md) — plug a graph in as a node
- [Human-in-the-loop](docs/recipes/human-in-the-loop.md) — interrupt, wait for human input, resume
- [Durable runs](docs/recipes/durable-runs.md) — JDBC or Redis checkpoint store, resume after crash
- [Resilient typed executor](docs/recipes/resilient-typed-executor.md) — tool audit + typed output + retry
- [Circuit breaker](docs/recipes/circuit-breaker.md) — trip upstream calls with Resilience4j

---

## Testing without an LLM

```java
MockAgent mock = MockAgent.builder()
        .thenReturn("First response")
        .thenReturn("Second response")
        .build();

TestGraph.Trace trace = TestGraph.trace(
        AgentGraph.builder()
            .addNode("a", mock)
            .addNode("b", MockAgent.returning("done"))
            .addEdge("a", "b"));

AgentResult result = trace.invoke(AgentContext.of("test"));

assertThat(trace.visitedInOrder("a", "b")).isTrue();
assertThat(result.text()).isEqualTo("done");
```

---

## Requirements

- Java 17+
- Spring Boot 3.x
- Spring AI 1.0+

---

## Roadmap

| Version | Focus |
|---------|-------|
| **0.4** (current) | Subgraphs, parallel fan-out, cancellation, typed output, `RetryPolicy`, `CircuitBreakerPolicy`, JDBC/Redis checkpoint store |
| **1.0** | API stabilization, documentation, community feedback |
| **1.1** | Crew roles (CrewAI-inspired), auto-config for checkpoint backends |
| **2.0** | OpenTelemetry tracing, MCP integration, Agent-as-Tool |

---

## Contributing

Contributions welcome! Please see [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines.

This project follows the [Apache 2.0 License](LICENSE).

---

## Inspiration

- [LangGraph](https://github.com/langchain-ai/langgraph) — graph-based orchestration
- [CrewAI](https://github.com/joaomdmoura/crewai) — role-based agent teams
- [AWS Strands](https://github.com/strands-agents/sdk-java) — agent patterns for Java
- [Spring AI](https://github.com/spring-projects/spring-ai) — the foundation we build on
