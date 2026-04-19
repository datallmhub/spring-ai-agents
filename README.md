# spring-ai-agents

**Coordinate multiple specialized agents in Spring Boot — without writing orchestration logic.**

[![build](https://github.com/asekka/spring-ai-agents/actions/workflows/build.yml/badge.svg)](https://github.com/asekka/spring-ai-agents/actions)
[![Java 17+](https://img.shields.io/badge/Java-17%2B-blue)](https://adoptium.net/)
[![Spring AI](https://img.shields.io/badge/Spring%20AI-1.0-green)](https://docs.spring.io/spring-ai/reference/)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

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
git clone https://github.com/asekka/spring-ai-agents.git
cd spring-ai-agents
mvn install -DskipTests -q
mvn -pl spring-ai-agents-samples exec:java \
    -Dexec.mainClass="io.github.asekka.springai.agents.samples.AdvancedGraphDemo"
```

Output:

```
=== Advanced Graph with Conditional Routing ===

[listener]  ENTER research
[research]  Gathering data on AI agents...
[listener]  EXIT  research (0ms)
[listener]  → research → analyze
[listener]  ENTER analyze
[analyze]   Confidence: 0.50 — needs more research
[listener]  EXIT  analyze (0ms)
[listener]  → analyze → research          ← conditional loop back!
[listener]  ENTER research
[research]  Gathering data on AI agents...
[listener]  EXIT  research (0ms)
[listener]  → research → analyze
[listener]  ENTER analyze
[analyze]   Confidence: 0.90 — analysis complete
[listener]  EXIT  analyze (0ms)
[listener]  → analyze → write             ← confidence OK, moving forward
[listener]  ENTER write
[write]     Writing final report...
[listener]  EXIT  write (0ms)
[listener]  ✓ Graph complete

Final report:
  AI agent frameworks enable multi-step LLM workflows.
  Confidence: 0.90. Research iterations: 2.
```

**All 3 samples:**

| Example | What it shows | Run with |
|---------|--------------|----------|
| `MinimalPipeline` | Two-step graph | `-Dexec.mainClass="...samples.MinimalPipeline"` |
| `MultiAgentCoordination` | Squad API + routing | `-Dexec.mainClass="...samples.MultiAgentCoordination"` |
| `AdvancedGraphDemo` | Conditional loops, state, listener | `-Dexec.mainClass="...samples.AdvancedGraphDemo"` |

---

## Working with real LLMs (Safety & Constraints)

While this framework handles complex logic routing, executing graphs against live APIs (Mistral, OpenAI, Anthropic) requires managing their physical limitations. `spring-ai-agents` incorporates safety features to counteract these common pitfalls:

**1. Strict Message Alternation (Safe Defaults)**
Some providers (like Mistral) will crash with `HTTP 400 Bad Request` if your prompt ends with an `AssistantMessage` or contains consecutive identical roles. During node chaining (A → B → C), `ExecutorAgent` uses built-in **Safe Message Ordering** to transparently interleave a padding `UserMessage` when required to prevent structure violations.

**2. Rate Limiting & Transient Errors**
Agentic graphs generate requests instantly. A 3-step pipeline fires 3 requests in under 2 seconds. Free-tier architectures (typically 1 request/sec) will fail with `429 Too Many Requests` or `503 Service Unavailable`.
*Solution*: Your components should rely on `.errorPolicy(ErrorPolicy.RETRY_ONCE)` mapped to Spring AI's automatic Retry mechanisms, or manually interleave non-blocking delays in intensive ReAct loops.

**3. Non-deterministic Routing**
A conditional edge verifying `result.text().contains("APPROVED")` can break if the agent suddenly decides to output `"Document is approved"` instead of exact text. Always implement fuzzy matching or use LLM structured JSON output mappings for robust conditional routing rules.

---

## Why spring-ai-agents?

Spring AI gives you a `ChatClient`. That's one agent talking to one LLM.

Real-world problems need **multiple agents working together**:

| Spring AI alone | spring-ai-agents |
|---|---|
| Single ChatClient call | Multi-agent coordination |
| Manual routing (`if`/`switch`) | LLM-driven or rule-based routing |
| No workflow structure | Graph-based orchestration |
| Stateless calls | Typed shared state (`StateKey<T>`) |
| DIY error handling | Built-in `ErrorPolicy` (retry, skip, fail-fast) |
| No observability | Micrometer metrics out of the box |

---

## Installation

Add the starter to your `pom.xml`:

```xml
<dependency>
    <groupId>io.github.asekka</groupId>
    <artifactId>spring-ai-agents-starter</artifactId>
    <version>0.3.0-SNAPSHOT</version>
</dependency>
```

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
│ spring-ai-agents-starter (auto-config, metrics) │
├──────────────────────┬──────────────────────────┤
│   squad              │   graph                  │
│   CoordinatorAgent   │   AgentGraph (runtime)   │
│   ExecutorAgent      │   Node, Edge             │
│   RoutingStrategy    │   ErrorPolicy            │
│   ReActAgent         │   AgentListener          │
├──────────────────────┴──────────────────────────┤
│ spring-ai-agents-core                           │
│ Agent, AgentContext, AgentResult, AgentEvent     │
│ StateKey, StateBag                              │
├─────────────────────────────────────────────────┤
│                   Spring AI                     │
│           ChatClient, ToolCallbacks             │
└─────────────────────────────────────────────────┘
```

---

## Built-in error handling

```java
AgentGraph.builder()
    .errorPolicy(ErrorPolicy.FAIL_FAST)   // stop on first error (default)
    .errorPolicy(ErrorPolicy.RETRY_ONCE)  // retry the failed node once
    .errorPolicy(ErrorPolicy.SKIP_NODE)   // log and continue
```

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
- [Human-in-the-loop](docs/recipes/human-in-the-loop.md) — interrupt, wait for human input, resume

---

## Modules

| Module | What it provides |
|--------|-----------------|
| `spring-ai-agents-core` | `Agent`, `AgentContext`, `AgentResult`, `AgentEvent`, `StateKey`, `StateBag` |
| `spring-ai-agents-graph` | `AgentGraph`, `Node`, `Edge`, `ErrorPolicy`, `AgentListener` |
| `spring-ai-agents-squad` | `CoordinatorAgent`, `ExecutorAgent`, `RoutingStrategy`, `ReActAgent` |
| `spring-ai-agents-test` | `MockAgent`, `TestGraph` — test without a real LLM |
| `spring-ai-agents-starter` | Auto-configuration, Micrometer, `application.yml` binding |

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
| **0.3** (current) | Core complete, HITL, checkpointing |
| **1.0** | API stabilization, documentation, community feedback |
| **1.1** | Crew roles (CrewAI-inspired), JDBC checkpointer |
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
