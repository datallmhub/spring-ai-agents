# Getting Started

A multi-agent orchestration layer for Spring AI, inspired by LangGraph / CrewAI
/ AWS Strands. The core idea: an `Agent` turns an `AgentContext` into an
`AgentResult`; an `AgentGraph` composes agents as nodes connected by edges.

## Requirements

- Java 17+
- Spring Boot 3.x
- Spring AI (any stable release) — only needed if you use `ExecutorAgent` or the starter

## Add the starter

Distributed via JitPack — declare the repository, then the starter:

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

## What's in 0.4

- **Subgraphs**: `AgentGraph` now implements `Agent`, so any graph can be plugged
  in as a node of another graph. Compose sub-pipelines without ceremony.
- **Usage accounting**: `AgentResult.usage()` carries an `AgentUsage(promptTokens,
  completionTokens, totalTokens)`. `ExecutorAgent` extracts it from Spring AI's
  `ChatResponse` metadata; `ParallelAgent`'s default combiner sums it across
  branches.
- **Parallel fan-out**: `ParallelAgent` runs N agents concurrently and joins
  their results through a `Combiner`. See
  [recipes/parallel-executors.md](recipes/parallel-executors.md).
- **Cancellation**: `graph.invoke(ctx, Duration.ofSeconds(30))` bounds the run
  with a deadline. Thread-interrupt is also honored between nodes — both surface
  as a failed `AgentResult` carrying the originating node.
- **Typed structured output**: `ExecutorAgent.builder().outputKey(MY_KEY)` binds
  the model response to a `StateKey<T>` using Spring AI's `responseEntity(...)`.
  Downstream nodes read the typed value via `ctx.get(MY_KEY)` — zero casts. See
  [resilient-typed-executor.md](recipes/resilient-typed-executor.md).
- **Tool-call handoff**: `AgentResult.toolCalls()` exposes every tool
  invocation as a `ToolCallRecord(name, arguments, result, error, durationMs)`
  for downstream audit nodes.
- **`RetryPolicy`**: exponential backoff with bounded jitter, configurable
  graph-wide (`AgentGraph.Builder.retryPolicy(...)`) and per-node
  (`addNode(name, agent, policy)`). Retains `ErrorPolicy.RETRY_ONCE` as a
  compatibility shim. See [resilient-typed-executor.md](recipes/resilient-typed-executor.md).
- **`CircuitBreakerPolicy`**: graph-module SPI with a no-op default; a
  Resilience4j adapter lives in `spring-agent-flow-resilience4j`. Applied
  per-node so retries iterate through the breaker. See
  [circuit-breaker.md](recipes/circuit-breaker.md).
- **Durable checkpoints**: `JdbcCheckpointStore` (portable upsert, H2/Postgres/
  MySQL) and `RedisCheckpointStore` (optional TTL) ship in
  `spring-agent-flow-checkpoint` alongside a Jackson codec with a
  `StateTypeRegistry` whitelist. See
  [durable-runs.md](recipes/durable-runs.md).

## What's in 0.3

- **Human-in-the-loop**: a node can return `AgentResult.interrupted("reason")`.
  The graph halts, persists a `Checkpoint` (if a `CheckpointStore` is configured),
  and returns the interrupted result. Call `graph.resume(runId, new UserMessage("..."))`
  to re-enter the same node with the new messages appended.
- **Checkpointing**: `AgentGraph.builder().checkpointStore(new InMemoryCheckpointStore())`
  enables automatic snapshots after every node when you start a run with
  `graph.invoke(ctx, runId)`. Successful runs delete their checkpoint on completion.
- **Supervisor pattern**: wrap a `CoordinatorAgent` in a `ReActAgent` — the
  coordinator re-routes on every iteration until a stop condition is met.
  See [supervisor-pattern.md](recipes/supervisor-pattern.md).

## What's in 0.2

- `AgentContext.applyResult()` now appends the assistant message automatically.
- `Edge.onResult(from, (ctx, result) -> ..., to)` — route based on the last node's
  `AgentResult`.
- `ReActAgent` — wrap any `Agent` into a self-looping agent.

## 60-second example

Build a two-step graph that summarizes then critiques a prompt. No Spring
context required — just compose plain Java objects.

```java
import io.github.asekka.springai.agents.core.AgentContext;
import io.github.asekka.springai.agents.core.AgentResult;
import io.github.asekka.springai.agents.graph.AgentGraph;
import io.github.asekka.springai.agents.squad.ExecutorAgent;

ExecutorAgent summarizer = ExecutorAgent.builder()
        .name("summarize")
        .chatClient(chatClient)
        .systemPrompt("Summarize the user message in one sentence.")
        .build();

ExecutorAgent critic = ExecutorAgent.builder()
        .name("critic")
        .chatClient(chatClient)
        .systemPrompt("Rate the previous summary 1-10 and explain.")
        .build();

AgentGraph graph = AgentGraph.builder()
        .addNode("summarize", summarizer)
        .addNode("critic", critic)
        .addEdge("summarize", "critic")
        .build();

AgentResult result = graph.invoke(
        AgentContext.of("The TGV is the French high-speed rail network..."));

System.out.println(result.text());
```

## Streaming

Every agent exposes `executeStream()` returning a `Flux<AgentEvent>`. The graph
forwards per-node tokens and emits `NodeTransition` between nodes.

```java
graph.invokeStream(AgentContext.of("hello"))
    .subscribe(event -> {
        switch (event) {
            case AgentEvent.Token t         -> System.out.print(t.chunk());
            case AgentEvent.NodeTransition x -> System.out.println("\n--> " + x.to());
            case AgentEvent.Completed c     -> System.out.println("\n[done]");
            default                          -> {}
        }
    });
```

## Configuration

See [Annex A of the design notes (FR)](design/cahier-des-charges.md). Minimal `application.yml`:

```yaml
spring:
  ai:
    agents:
      enabled: true
      default-error-policy: RETRY_ONCE
      observability:
        metrics: true
```

Properties are bound by `spring-agent-flow-starter`. When a `MeterRegistry` bean
is present, the starter registers a `MicrometerAgentListener` that emits
`agents.execution.count`, `agents.execution.duration`, `agents.graph.transitions`,
and `agents.execution.errors`.

## Next steps

- [Recipe: ReAct loop](recipes/react-loop.md)
- [Recipe: Supervisor pattern](recipes/supervisor-pattern.md)
- [Recipe: Parallel executors](recipes/parallel-executors.md)
- [Recipe: Subgraphs](recipes/subgraphs.md)
- [Recipe: Human-in-the-loop](recipes/human-in-the-loop.md)
- [Recipe: Durable runs](recipes/durable-runs.md)
- [Recipe: Resilient typed executor](recipes/resilient-typed-executor.md)
- [Recipe: Circuit breaker](recipes/circuit-breaker.md)
