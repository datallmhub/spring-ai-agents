# Recipe — Subgraphs

Starting in 0.4, `AgentGraph` implements `Agent`. That means any graph can be
plugged in as a node of a bigger graph — no adapter, no wrapper.

Use subgraphs when a slice of your pipeline is itself a self-contained
multi-step workflow that you want to develop, test, and reuse independently.

```java
AgentGraph research = AgentGraph.builder()
        .name("research")
        .addNode("search", searchAgent)
        .addNode("rank", rankAgent)
        .addNode("summarize", summarizeAgent)
        .addEdge("search", "rank")
        .addEdge("rank", "summarize")
        .build();

AgentGraph writer = AgentGraph.builder()
        .name("writer")
        .addNode("outline", outlineAgent)
        .addNode("draft", draftAgent)
        .addEdge("outline", "draft")
        .build();

AgentGraph pipeline = AgentGraph.builder()
        .name("article-pipeline")
        .addNode("research", research)   // <- subgraph as a node
        .addNode("writer", writer)       // <- subgraph as a node
        .addNode("publish", publishAgent)
        .addEdge("research", "writer")
        .addEdge("writer", "publish")
        .build();

AgentResult result = pipeline.invoke(AgentContext.of("Write about TGV trains"));
```

## What the parent sees

When a subgraph runs as a node, its final `AgentResult` becomes the node's
result. Its `stateUpdates` merge into the parent context, so downstream nodes
in the outer graph observe the inner graph's work transparently.

## Streaming across nesting

`invokeStream` composes too: per-node tokens from inner nodes are forwarded
upward, tagged with the inner graph's node name. `NodeTransition` events
surface for the outer graph's edges; inner transitions stay internal.

## Testing subgraphs in isolation

Each subgraph is a regular `AgentGraph`. Write unit tests against the inner
graph directly; integrate the outer graph once the inner contract is stable.

## Trade-offs

- **Pros.** Encourages decomposition; each subgraph has a clear input/output
  contract and can be reused across pipelines.
- **Cons.** Errors inside a subgraph surface as a failed result for the *outer*
  node. If you need node-level granularity in tracing, hook an `AgentListener`
  on both the inner and outer graph.
