# Recipe — Human-in-the-loop

Pause a graph mid-run, collect input from a human, and resume exactly where you
stopped. Two pieces make it work: `AgentResult.interrupted(reason)` and a
`CheckpointStore`.

## The flow

1. A node decides it needs input → returns `AgentResult.interrupted("why")`.
2. The graph persists a `Checkpoint` keyed by `runId` and returns the
   interrupted result to the caller.
3. Your application surfaces the reason to the user, collects their reply.
4. Your application calls `graph.resume(runId, new UserMessage(reply))`.
5. The graph loads the checkpoint, appends the reply to the conversation, and
   re-runs the interrupted node with the richer context.

## Minimal example

```java
import io.github.asekka.springai.agents.core.*;
import io.github.asekka.springai.agents.graph.*;
import org.springframework.ai.chat.messages.UserMessage;

InMemoryCheckpointStore store = new InMemoryCheckpointStore();

Agent approvalGate = ctx -> {
    boolean approved = ctx.messages().stream()
            .map(m -> m.getText().toLowerCase())
            .anyMatch(t -> t.contains("approve"));
    return approved
            ? AgentResult.ofText("approved")
            : AgentResult.interrupted("Please approve or reject this plan");
};

Agent execute = ctx -> AgentResult.ofText("done");

AgentGraph graph = AgentGraph.builder()
        .addNode("gate", approvalGate)
        .addNode("execute", execute)
        .addEdge("gate", "execute")
        .checkpointStore(store)
        .build();

// First call: halts at the gate
AgentResult pending = graph.invoke(AgentContext.of("Ship the changes?"), "run-1");
assert pending.isInterrupted();
System.out.println(pending.interrupt().reason());

// ... show the reason to the user, they reply "approve" ...

AgentResult done = graph.resume("run-1", new UserMessage("approve"));
assert "done".equals(done.text());
```

## Checkpointing semantics

- Every successful node transition persists a snapshot (new `nextNode`, full
  context, iteration count).
- When a run completes normally, its checkpoint is deleted. Cleanup is
  automatic — no dangling state.
- When a node returns `AgentResult.interrupted(...)`, the snapshot keeps
  `nextNode = currentNode` so `resume` re-runs the same node.
- `graph.resume(runId, additional...)` appends the given messages before
  re-entering the node, which is how the user's reply reaches the agent.

## Plugging in a real store

`CheckpointStore` is a two-method interface (`save`, `load`, `delete`). Swap
`InMemoryCheckpointStore` for a JDBC, Redis, or S3-backed implementation when
you need durability across JVM restarts. Serialize `AgentContext` by writing
out the messages (they are Spring AI `Message` instances) plus the `StateBag`
entries.
