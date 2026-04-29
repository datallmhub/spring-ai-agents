# Contributing

Contributions of any size are welcome — bug reports, focused PRs, recipe
improvements, new adapters. This page covers the practical bits: how to build,
how the codebase is laid out, and the conventions a PR is expected to follow.

## Prerequisites

- Java 17+
- Maven 3.9+
- Optional: Docker (only if you run a Postgres or Redis on the side; the
  bundled checkpoint tests use H2 and a Mockito double)

## Build and test

```bash
git clone https://github.com/datallmhub/spring-agent-flow.git
cd spring-agent-flow
mvn clean verify
```

Run the no-LLM demo to sanity-check your local checkout:

```bash
mvn -pl spring-agent-flow-samples exec:java
```

Run a single module's tests:

```bash
mvn -pl spring-agent-flow-graph -am test
```

The CI on `main` builds against Java 17 and 21 with `mvn -B verify -P
coverage-check`. Match that locally before opening a PR.

## Project layout

The repository is a Maven multi-module project. Each module has a single
responsibility:

| Module | Scope |
|---|---|
| `core` | API primitives only (`Agent`, `AgentContext`, `StateKey`, …). No transitive dependency on Spring AI. |
| `graph` | `AgentGraph` runtime, `RetryPolicy`, `CircuitBreakerPolicy` SPI, `CheckpointStore` contract. |
| `squad` | Higher-level agents: `CoordinatorAgent`, `ExecutorAgent`, `ReActAgent`, `ParallelAgent`. |
| `checkpoint` | `JdbcCheckpointStore`, `RedisCheckpointStore`, Jackson codec. |
| `resilience4j` | Adapter implementing `CircuitBreakerPolicy` against Resilience4j. |
| `starter` | Spring Boot auto-config, properties, Micrometer listener. |
| `samples` | Runnable demos (no API key required by default). |
| `test` | Test fixtures (`MockAgent`, `TestGraph`) shared across module test suites. |

Keep new adapters and integrations behind a fresh module rather than enlarging
an existing one. Don't push provider-specific code into `graph` or `core`.

## Commit conventions

Subject lines use the imperative mood, fit on ~70 characters, and describe the
change rather than the file. The body (optional) explains *why*, not *what*.

```
Expose ParallelAgent.maxConcurrency on the builder

The pool size was hard-coded to min(branches, 8), which blocks throttling
against rate-limited upstreams. maxConcurrency(int) is now configurable on
the builder, default 8 to preserve current behaviour.
```

Squash unrelated changes into separate commits. A PR can carry several
commits as long as each one is self-contained.

## Pull requests

- Fork, branch from `main`, push to your fork, open the PR against
  `datallmhub/spring-agent-flow:main`.
- Describe the motivation in the PR body, not just the diff. If the change
  affects API surface or default behaviour, call it out explicitly.
- Add or update tests for any behavioural change. The `spring-agent-flow-test`
  module exposes `MockAgent` / `TestGraph` to keep tests LLM-free.
- Update the relevant recipe under `docs/recipes/` when you add or change a
  user-facing capability.
- CI must be green before review.

## Reporting bugs

Open an issue with:

- the version (`v0.4.x`),
- a minimal reproducer (a short `main()` is ideal — the smoke-test pattern in
  the consumer-test demo works well),
- the observed vs. expected behaviour.

## License

By contributing you agree that your code will be released under the
[Apache License 2.0](LICENSE).
