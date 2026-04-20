# Recipe — Resilient executor with typed output and tool-call handoff

Three features compose when an LLM step must:

1. Call tools and **hand the call list off** to downstream nodes for audit or
   follow-up work.
2. Produce a **typed** response (a record, a POJO) rather than raw text.
3. **Retry** transient provider errors without retrying the whole graph.

This recipe wires all three together on a single `ExecutorAgent` node.

## 1. Typed output via `outputKey`

Bind a `StateKey` on the executor. Spring AI converts the model response to the
key's type and the executor writes it into `AgentContext` in one step — no
parsing, no casts in downstream edges.

```java
import io.github.asekka.springai.agents.core.*;
import io.github.asekka.springai.agents.squad.ExecutorAgent;

public record TriageReport(String severity, String summary, List<String> tags) {}

StateKey<TriageReport> TRIAGE = StateKey.of("triage", TriageReport.class);

Agent triageStep = ExecutorAgent.builder()
        .chatClient(chatClient)
        .systemPrompt("Classify the incoming support ticket. Answer with the TriageReport schema.")
        .outputKey(TRIAGE)
        .build();
```

At runtime the executor returns an `AgentResult` whose `structuredOutput()`
holds the `TriageReport` instance and whose `stateUpdates()` already contains
`TRIAGE -> report`. Downstream code reads it with `ctx.get(TRIAGE)` — zero
casts.

## 2. Tool-call handoff

If the executor has `ToolCallback`s, every call the model makes during the
turn is recorded as a `ToolCallRecord` (sequence, name, arguments, result or
error, duration) and exposed via `AgentResult.toolCalls()`. This is the hand-off
point for a downstream auditor or policy node.

```java
Agent toolUser = ExecutorAgent.builder()
        .chatClient(chatClient)
        .tools(searchTool, ticketingTool)
        .outputKey(TRIAGE)
        .build();

AgentGraph graph = AgentGraph.builder()
        .addNode("triage", toolUser)
        .addNode("audit", ctx -> {
            // Downstream node inspects the call list that was just produced.
            AgentResult last = /* obtained via listener or AgentResult chaining */;
            for (ToolCallRecord call : last.toolCalls()) {
                log.info("tool={} args={} ok={}", call.name(), call.arguments(), call.success());
            }
            return AgentResult.ofText("audited");
        })
        .addEdge("triage", "audit")
        .build();
```

`ToolCallRecord.success()` tells you whether the callback threw; the
`durationMs` is useful for SLO alerts when a specific tool starts dragging.

## 3. Retry policy — graph-wide and per-node

Graph-wide policy handles the common case: every node inherits the same
backoff. Per-node override takes precedence for risky calls.

```java
import io.github.asekka.springai.agents.graph.*;

// Only retry transient network errors, 3 attempts, 200ms → 2s backoff with
// ±20% jitter.
RetryPolicy llmRetry = RetryPolicy.exponential(3, Duration.ofMillis(200));

AgentGraph graph = AgentGraph.builder()
        .addNode("triage", toolUser, llmRetry)   // per-node override
        .addNode("audit", auditNode)             // graph default (none)
        .addEdge("triage", "audit")
        .retryPolicy(RetryPolicy.none())         // graph default
        .errorPolicy(ErrorPolicy.FAIL_FAST)
        .build();
```

Key points:

- **`RetryPolicy.exponential(n, base)`** uses bounded jitter `[cap*(1-f), cap]`
  so retries never exceed `maxDelay` — safe under tight SLOs.
- **Per-node wins.** `addNode(name, agent, policy)` sets an override that
  bypasses the graph default, so only the LLM call retries — the audit node
  fails fast.
- **Predicate controls what is retried.** The default predicate
  (`RetryPredicates.transientIo()`) matches `IOException` and
  `TimeoutException`; swap it to `RetryPredicates.always()` for tests, or pass
  a custom `Predicate<Throwable>` to the `RetryPolicy` constructor to retry on
  provider-specific rate-limit exceptions.
- **Retries are not stored in the checkpoint** — if a retry fails and the
  graph has a `CheckpointStore`, only the final error gets persisted.

## 4. Putting it together

```java
AgentGraph graph = AgentGraph.builder()
        .addNode("triage", ExecutorAgent.builder()
                .chatClient(chatClient)
                .systemPrompt("Classify the ticket. Use the tools before answering.")
                .tools(searchTool, ticketingTool)
                .outputKey(TRIAGE)
                .build(),
                RetryPolicy.exponential(3, Duration.ofMillis(200)))
        .addNode("route", ctx -> {
            TriageReport r = ctx.get(TRIAGE);
            return AgentResult.builder()
                    .text("routed as " + r.severity())
                    .completed(true)
                    .build();
        })
        .addEdge(Edge.conditional("triage",
                ctx -> "critical".equals(ctx.get(TRIAGE).severity()),
                "route"))
        .retryPolicy(RetryPolicy.none())
        .build();

AgentResult result = graph.invoke(AgentContext.of("Server down since 14:02 UTC"));
```

One node, three capabilities: typed routing state, tool audit trail, and
scoped retries — without any of them leaking into the rest of the graph.
