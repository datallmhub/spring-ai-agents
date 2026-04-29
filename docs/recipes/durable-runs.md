# Recipe — Durable runs with JDBC or Redis checkpoints

A checkpoint is a snapshot of a run's state (`AgentContext`, next node,
iteration count, interrupt reason if any) persisted after every successful
node. It enables two things:

1. **Resume after crash.** Restart the process, call
   `graph.resume(runId)`, and execution continues from the last saved node.
2. **Human-in-the-loop across requests.** A node returns
   `AgentResult.interrupted("wait for approval")`; the graph saves the
   checkpoint and returns. A later HTTP request calls `resume` with the
   approval message.

## 1. Module

Add the checkpoint module. The Redis store is optional and only activates
when `spring-data-redis` is on the classpath.

```xml
<dependency>
    <groupId>com.github.datallmhub.spring-ai-agents</groupId>
    <artifactId>spring-ai-agents-checkpoint</artifactId>
    <version>v0.4.1</version>
</dependency>
```

## 2. Register your state types

Checkpoints store state entries under **logical** type names (not raw FQNs)
so you can rename a class without breaking existing checkpoints and without
leaking package structure to storage.

```java
public record TriageReport(String severity, String summary) {}

StateKey<TriageReport> TRIAGE = StateKey.of("triage", TriageReport.class);

StateTypeRegistry registry = new StateTypeRegistry().register(TRIAGE);
// Or: new StateTypeRegistry().register("triage-v1", TriageReport.class)
```

Encoding a state value whose type isn't registered throws
`CheckpointSerializationException` — you catch unregistered types at save
time, not at resume time.

## 3. Pick a codec + store

```java
CheckpointCodec codec = new JacksonCheckpointCodec(registry);
```

### JDBC (Postgres, MySQL, H2)

```java
JdbcCheckpointStore store = new JdbcCheckpointStore(jdbcTemplate, txManager, codec);
store.createTableIfMissing();   // once at startup
```

The default table is `agent_checkpoint (run_id PK, payload CLOB, checkpoint_version,
created_at, updated_at)`. Save performs an `UPDATE`-then-`INSERT-if-zero-rows`
inside a `READ_COMMITTED` transaction, portable across the three databases
above.

Pass a custom table name as the fourth constructor argument:

```java
new JdbcCheckpointStore(jdbcTemplate, txManager, codec, "support_runs");
```

### Redis (optional)

Requires `spring-data-redis` + a driver (Lettuce/Jedis):

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

```java
RedisTemplate<String, String> redis = /* configured by Spring Boot */;
CheckpointStore store = new RedisCheckpointStore(redis, codec,
        "support:ckpt:", Duration.ofHours(24));  // prefix + TTL
```

Every save is a single `SET` (optionally with TTL); load and delete are
single ops too, so writes are atomic without any coordination.

## 4. Wire it into the graph

```java
AgentGraph graph = AgentGraph.builder()
        .addNode("triage", triageAgent)
        .addNode("approve", approvalGate)        // returns interrupted(...)
        .addNode("dispatch", dispatcher)
        .addEdge("triage", "approve")
        .addEdge("approve", "dispatch")
        .checkpointStore(store)
        .build();

String runId = "ticket-" + ticket.id();
AgentResult first = graph.invoke(AgentContext.of(ticket.text()), runId);
if (first.isInterrupted()) {
    // Save runId somewhere (DB, URL param). Return 202 to the caller.
}
```

Later, when the approval arrives:

```java
AgentResult resumed = graph.resume(runId, new UserMessage("approved by alice"));
```

`resume` reloads the checkpoint, appends the provided messages to the stored
context, and re-enters the node that was interrupted. Successful runs
auto-delete their row when the graph completes.

## 5. What is and isn't stored

- **Stored**: run id, next node to execute, iteration count, all messages
  in `AgentContext`, every entry of the state bag (under its logical type
  name), and the interrupt reason if the run paused.
- **Not stored**: in-flight retry counters, listener state, `ToolCallRecord`
  details (they belong to the `AgentResult` of the last node and aren't
  carried across resume), or `ChatClient` configuration.

## 6. Migrating checkpoint payloads

The payload carries a `version` field (currently `1`). Bumping it rejects
older payloads at decode time with
`CheckpointSerializationException("Unsupported checkpoint version: ...")`.
Write a migration that decodes the old version, transforms the payload,
re-encodes at the new version.
