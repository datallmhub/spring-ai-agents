# Recipe — ReAct loop

A ReAct agent alternates *reasoning* and *acting* until it decides it is done.
Two ways to build it:

1. **`ReActAgent` wrapper (v0.2+)** — preferred for a single self-looping agent.
2. **Self-referencing edge** — use when the loop is one node inside a larger
   graph and you want to reuse the graph's own `maxIterations` guard.

## Option 1 — `ReActAgent` (v0.2+)

```java
import io.github.asekka.springai.agents.core.*;
import io.github.asekka.springai.agents.squad.*;

Agent reasoner = ExecutorAgent.builder()
        .chatClient(chatClient)
        .systemPrompt("""
                Reply with JSON: {"final": true|false, "answer": "..."}.
                Set final=true only when the answer is complete.
                """)
        .tools(webSearchTool, calculatorTool)
        .build();

ReActAgent loop = ReActAgent.builder()
        .inner(reasoner)
        .maxSteps(6)
        .stopWhen((ctx, res) -> res.text() != null && res.text().contains("\"final\": true"))
        .build();

AgentResult result = loop.execute(AgentContext.of("Which city in France had the most sunshine last July?"));
```

`ReActAgent` automatically calls `context.applyResult(result)` between
iterations, so the assistant message is appended to the conversation before
the next call. The loop stops when `stopWhen` returns true (default:
`result.completed()`) or when `maxSteps` is reached (which yields an
`AgentResult.failed(...)`).

## Option 2 — Self-edge inside a graph

```java
import io.github.asekka.springai.agents.core.*;
import io.github.asekka.springai.agents.graph.*;

StateKey<Boolean> DONE  = StateKey.of("done", Boolean.class);
StateKey<Integer> STEPS = StateKey.of("steps", Integer.class);

Agent reactStep = context -> {
    int steps = context.get(STEPS) == null ? 0 : context.get(STEPS);

    // 1. reason about the context
    // 2. either call a tool or produce a final answer
    boolean finished = steps >= 3; // your own stop condition

    return AgentResult.builder()
            .text(finished ? "final answer" : "intermediate thought")
            .stateUpdates(Map.of(
                    STEPS, steps + 1,
                    DONE, finished))
            .build();
};

AgentGraph react = AgentGraph.builder()
        .addNode("react", reactStep)
        .addEdge(Edge.conditional("react", ctx -> !Boolean.TRUE.equals(ctx.get(DONE)), "react"))
        .maxIterations(10)            // hard safety bound
        .build();
```

Key points:

- **Cycles are first-class.** An edge from a node to itself is allowed.
- **Stop via state, not exceptions.** The conditional edge checks the `DONE`
  flag. When the predicate returns `false`, no outgoing edge matches and the
  graph terminates with the last node's result.
- **`maxIterations`** is your backstop: if the loop never converges, the graph
  fails with a `Max iterations exceeded` error after N attempts.
- **One `in_progress` tool call per iteration** — use `ExecutorAgent` with a
  `ToolCallback` list when the step needs to call tools.
