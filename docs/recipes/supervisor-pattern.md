# Recipe — Supervisor pattern

A supervisor delegates to one of several specialized executors based on the
task. Use `CoordinatorAgent` with a `RoutingStrategy`.

## LLM-driven routing

The coordinator asks the LLM to pick the best executor name from the registered
set.

```java
import io.github.asekka.springai.agents.squad.*;

ExecutorAgent researcher = ExecutorAgent.builder()
        .name("research")
        .chatClient(chatClient)
        .systemPrompt("You are a research specialist. Cite sources.")
        .build();

ExecutorAgent coder = ExecutorAgent.builder()
        .name("coder")
        .chatClient(chatClient)
        .systemPrompt("You are a senior Java engineer. Output only code.")
        .build();

ExecutorAgent writer = ExecutorAgent.builder()
        .name("writer")
        .chatClient(chatClient)
        .systemPrompt("You are a technical writer. Produce clear prose.")
        .build();

CoordinatorAgent supervisor = CoordinatorAgent.builder()
        .name("supervisor")
        .executor("research", researcher)
        .executor("coder", coder)
        .executor("writer", writer)
        .routingStrategy(RoutingStrategy.llmDriven(chatClient))
        .build();

AgentResult result = supervisor.execute(
        AgentContext.of("Write a Java snippet that reverses a linked list"));
// → routes to "coder"
```

## Fixed or custom routing

- `RoutingStrategy.first()` — always picks the first registered executor (useful for tests)
- `RoutingStrategy.fixed("name")` — always routes to a given executor
- Implement `RoutingStrategy` yourself for rule-based routing (regex, keyword,
  content classifier, etc.)

```java
RoutingStrategy byKeyword = (ctx, names) -> {
    String text = ctx.messages().stream()
            .map(m -> m.getText().toLowerCase())
            .reduce("", (a, b) -> a + " " + b);
    if (text.contains("code") || text.contains("java")) return "coder";
    if (text.contains("source") || text.contains("citation")) return "research";
    return "writer";
};
```

## As a graph node

The coordinator is itself an `Agent`, so you can drop it into a graph:

```java
AgentGraph.builder()
        .addNode("supervise", supervisor)
        .addNode("review", reviewer)
        .addEdge("supervise", "review")
        .build();
```
