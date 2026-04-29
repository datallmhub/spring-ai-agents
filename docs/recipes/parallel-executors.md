# Recipe — Parallel executors

Starting in 0.4 the squad module ships `ParallelAgent`, a built-in fan-out /
gather `Agent` that runs N branches concurrently and joins their results via a
`Combiner`. No hand-rolled Reactor plumbing required.

```java
import io.github.asekka.springai.agents.core.Agent;
import io.github.asekka.springai.agents.core.AgentContext;
import io.github.asekka.springai.agents.core.AgentResult;
import io.github.asekka.springai.agents.squad.ParallelAgent;

Agent fanOut = ParallelAgent.builder()
        .name("fan-out")
        .branch("legal", legalAgent)
        .branch("finance", financeAgent)
        .branch("hr", hrAgent)
        .combiner(ParallelAgent.Combiner.concatTexts("\n---\n"))
        .timeout(Duration.ofSeconds(30))
        .build();

AgentResult result = fanOut.execute(AgentContext.of("What's blocking Q3?"));
```

Branches run on a bounded thread pool. The iteration order is the declaration
order — combiners receive an ordered `Map<String, AgentResult>`.

## Bounding concurrency

By default the pool is sized at `min(N, 8)` where `N` is the number of branches.
Override with `maxConcurrency` when you need to throttle expensive upstreams
(rate-limited LLMs, paid APIs, scarce DB connections):

```java
ParallelAgent.builder()
        .branch("openai-1", a)
        .branch("openai-2", b)
        .branch("openai-3", c)
        .maxConcurrency(2)        // at most 2 branches in flight
        .build();
```

`maxConcurrency(1)` serialises branches in declaration order — useful in tests
or when an upstream cannot tolerate any concurrency. Values must be `>= 1`.

## Combining results

`Combiner.concatTexts(separator)` is the built-in default: it joins branch texts
and sums their `AgentUsage` into the final result. For anything else, implement
the interface directly:

```java
ParallelAgent.Combiner pickBestScore = (ctx, perBranch) -> perBranch.values().stream()
        .filter(r -> !r.hasError())
        .max(Comparator.comparingInt(r -> parseScore(r.text())))
        .orElseGet(() -> AgentResult.ofText("no branch succeeded"));
```

## Composing with a graph

`ParallelAgent` is just an `Agent`, so drop it in as a node:

```java
AgentGraph graph = AgentGraph.builder()
        .addNode("fan-out", fanOut)
        .addNode("synthesize", synthesizer)
        .addEdge("fan-out", "synthesize")
        .build();
```

## Error handling

A branch that throws surfaces as an `AgentResult.failed(...)` for that branch —
the other branches still complete and the combiner sees all results. If you want
the whole fan-out to fail when any branch fails, filter in your `Combiner`:

```java
ParallelAgent.Combiner failOnAny = (ctx, perBranch) -> perBranch.entrySet().stream()
        .filter(e -> e.getValue().hasError())
        .findFirst()
        .map(e -> e.getValue())
        .orElseGet(() -> ParallelAgent.Combiner.concatTexts("\n").combine(ctx, perBranch));
```

The overall `timeout` is enforced on the join: if it elapses before all
branches complete, `ParallelAgent` returns a failed result carrying a
`TimeoutException`.

## Trade-offs

- **Pros.** Declarative fan-out, ordered results, usage summed automatically,
  timeout built in.
- **Cons.** No cross-branch state merging during execution — branches share the
  inbound `AgentContext` (read-only as far as the others are concerned). If you
  need per-branch state propagated back into the graph, merge it inside your
  `Combiner` via `AgentResult.Builder.stateUpdates(...)`.
- **Tool calls.** Each branch's `ChatClient` is independent; provider rate
  limits apply per-branch. Use `maxConcurrency(...)` to cap load against shared
  upstreams.
