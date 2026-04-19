# Recipe — ReAct loop

A ReAct agent alternates *reasoning* and *acting* until it decides it is done.
Implement it as a single node whose outgoing edge loops back to itself until a
flag in the shared state says `done == true`. The graph's `maxIterations` guard
prevents runaway loops.

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
