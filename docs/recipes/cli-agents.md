# Recipe — Hybrid graphs with external CLI agents (Claude Code, Codex, Gemini, …)

`spring-agent-flow-cli-agents` lets you drop a
[`spring-ai-community/spring-ai-agents`](https://github.com/spring-ai-community/spring-ai-agents)
provider — Claude Code, Codex, Gemini CLI, Amazon Q, Amp, Qwen, SWE — into
an `AgentGraph` as if it were any other `Agent`.

The two projects are orthogonal. Their `AgentClient`/`AgentModel` give you a
**unified API for autonomous CLI agents**; this module wraps that API so the
agents can participate in the **stateful workflow runtime** of
spring-agent-flow. Result: a single graph can mix `ChatClient`-driven
`ExecutorAgent`s with subprocess-driven CLI agents.

## 1. Module

```xml
<dependency>
    <groupId>com.github.datallmhub.spring-agent-flow</groupId>
    <artifactId>spring-agent-flow-cli-agents</artifactId>
    <version>v0.5.0</version>
</dependency>
```

This pulls `org.springaicommunity.agents:agent-model`, the SPI behind
`AgentClient`. Provider-specific JARs (Claude, Codex, …) come from the
upstream project — declare whichever ones you need.

## 2. Wrap an `AgentModel` as an `Agent`

```java
import io.github.asekka.springai.agents.cliagents.CliAgentNode;
import org.springaicommunity.agents.claude.ClaudeAgentModel;
import org.springaicommunity.agents.claude.ClaudeAgentOptions;

ClaudeAgentModel claude = ClaudeAgentModel.builder()
        .defaultOptions(ClaudeAgentOptions.builder()
                .model("claude-sonnet-4-5")
                .yolo(true)
                .build())
        .build();

Agent claudeNode = CliAgentNode.builder()
        .name("claude")
        .model(claude)
        .workingDirectory(Paths.get("/path/to/project"))
        .build();
```

The node:

- Extracts the **goal** from the `AgentContext` (last user message, by default)
- Forwards a **fixed working directory** for the subprocess
- Maps the returned `AgentResponse` into an `AgentResult` (success → text,
  unsuccessful → failed result that the graph's `ErrorPolicy` can handle)
- Stores the raw `AgentResponse` in `AgentResult.structuredOutput()` so
  downstream nodes can inspect provider-specific metadata

## 3. Goal extraction

The default extractor is the text of the last user message in the context.
Override it when the goal lives elsewhere (typed state, a header, a fixed
constant):

```java
StateKey<String> TICKET = StateKey.of("ticket", String.class);

CliAgentNode.builder()
        .model(claude)
        .workingDirectory(workspace)
        .goalExtractor(ctx -> "Triage and fix: " + ctx.get(TICKET))
        .build();

// Or for a constant goal that ignores the context:
CliAgentNode.builder().model(claude).workingDirectory(workspace)
        .fixedGoal("Run mvn clean verify and commit any fixes")
        .build();
```

A blank or null goal surfaces as a failed `AgentResult` rather than
hitting the subprocess.

## 4. Hybrid workflow — ChatClient + CLI agent in one graph

```java
ExecutorAgent triage = ExecutorAgent.builder()
        .chatClient(chatClient)
        .systemPrompt("Classify the bug report. Output the affected module name.")
        .outputKey(MODULE_KEY)
        .build();

Agent fixer = CliAgentNode.builder()
        .name("claude-fixer")
        .model(claude)
        .workingDirectory(repoRoot)
        .goalExtractor(ctx -> "Fix the failing tests in module: " + ctx.get(MODULE_KEY))
        .build();

ExecutorAgent reviewer = ExecutorAgent.builder()
        .chatClient(chatClient)
        .systemPrompt("Review the diff. Approve or request changes.")
        .build();

AgentGraph graph = AgentGraph.builder()
        .addNode("triage", triage)
        .addNode("fix",    fixer,    RetryPolicy.exponential(2, Duration.ofSeconds(5)))
        .addNode("review", reviewer)
        .addEdge("triage", "fix")
        .addEdge("fix", "review")
        .errorPolicy(ErrorPolicy.FAIL_FAST)
        .build();
```

`triage` is an LLM call via Spring AI's `ChatClient`; `fix` shells out to
Claude Code in the repo; `review` is back to the LLM. The retry policy
applies to the subprocess call as it would to any node.

## 5. Resilience and durability

`CliAgentNode` is a regular `Agent`, so everything in the resilience and
checkpoint stack composes:

- **`RetryPolicy`** — retry transient subprocess crashes with bounded jitter.
- **`CircuitBreakerPolicy`** — short-circuit calls when the upstream binary
  is unhealthy (e.g., Claude rate-limited).
- **`CheckpointStore`** — interrupt before the CLI call, persist the
  context, resume after human approval. The subprocess does not run a second
  time on resume because the checkpoint records the next node.

## 6. What is and isn't bridged

| Concern | Status |
|---|---|
| Goal text | ✅ extracted from `AgentContext` |
| Working directory | ✅ fixed per node (per-execution override is a future option) |
| `AgentOptions` | ✅ via `Builder.defaultOptions(...)` |
| Success/failure mapping | ✅ via `AgentResponse.isSuccessful()` |
| Streaming output | ❌ not yet — `CliAgentNode.execute()` is blocking |
| Advisors / MCP servers | ❌ pass them through `AgentOptions` or build a custom `AgentClient` |
| Per-call cost / token usage | ❌ not surfaced into `AgentUsage` (provider metadata is on `structuredOutput()`) |
