# Getting Started

A multi-agent orchestration layer for Spring AI, inspired by LangGraph / CrewAI
/ AWS Strands. The core idea: an `Agent` turns an `AgentContext` into an
`AgentResult`; an `AgentGraph` composes agents as nodes connected by edges.

## Requirements

- Java 17+
- Spring Boot 3.x
- Spring AI (any stable release) — only needed if you use `ExecutorAgent` or the starter

## Add the starter

```xml
<dependency>
    <groupId>io.github.asekka</groupId>
    <artifactId>spring-ai-agents-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

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

See [Annex A of the spec](../CAHIER-DES-CHARGES.md). Minimal `application.yml`:

```yaml
spring:
  ai:
    agents:
      enabled: true
      default-error-policy: RETRY_ONCE
      observability:
        metrics: true
```

Properties are bound by `spring-ai-agents-starter`. When a `MeterRegistry` bean
is present, the starter registers a `MicrometerAgentListener` that emits
`agents.execution.count`, `agents.execution.duration`, and
`agents.execution.errors`.

## Next steps

- [Recipe: ReAct loop](recipes/react-loop.md)
- [Recipe: Supervisor pattern](recipes/supervisor-pattern.md)
- [Recipe: Parallel executors](recipes/parallel-executors.md)
