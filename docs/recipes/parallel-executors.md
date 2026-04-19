# Recipe — Parallel executors

V1's `AgentGraph` executes one node at a time. For fan-out / gather scenarios
(e.g. ask N specialists, merge answers), wrap the parallelism inside a single
composite `Agent` that uses Reactor.

```java
import io.github.asekka.springai.agents.core.*;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

record ParallelAgent(String name, List<Agent> members) implements Agent {

    @Override
    public AgentResult execute(AgentContext context) {
        List<String> answers = Flux.fromIterable(members)
                .parallel()
                .runOn(Schedulers.boundedElastic())
                .map(a -> a.execute(context))
                .sequential()
                .map(AgentResult::text)
                .collectList()
                .block();

        return AgentResult.ofText(String.join("\n---\n", answers));
    }
}
```

Then compose as usual:

```java
Agent fanOut = new ParallelAgent("fan-out", List.of(legalAgent, financeAgent, hrAgent));

AgentGraph graph = AgentGraph.builder()
        .addNode("fan-out", fanOut)
        .addNode("synthesize", synthesizer)
        .addEdge("fan-out", "synthesize")
        .build();
```

## Trade-offs

- **Pros.** Keeps the public graph API simple. Parallelism is local to one node
  and doesn't leak into the orchestration model.
- **Cons.** No cross-branch state merging — the parallel node produces a single
  merged result. If you need per-branch state in the graph, wait for V2's squad
  extensions.
- **Tool calls.** Each member's `ChatClient` is independent; rate limits apply
  per-provider. Use a bounded scheduler to cap concurrency.
