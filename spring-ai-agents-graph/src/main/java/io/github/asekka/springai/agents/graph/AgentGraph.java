package io.github.asekka.springai.agents.graph;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import io.github.asekka.springai.agents.core.Agent;
import io.github.asekka.springai.agents.core.AgentContext;
import io.github.asekka.springai.agents.core.AgentError;
import io.github.asekka.springai.agents.core.AgentEvent;
import io.github.asekka.springai.agents.core.AgentResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.lang.Nullable;
import reactor.core.publisher.Flux;

public final class AgentGraph {

    private static final Logger log = LoggerFactory.getLogger(AgentGraph.class);

    private final String name;
    private final Map<String, Node> nodes;
    private final List<Edge> edges;
    private final String entryNode;
    private final ErrorPolicy errorPolicy;
    private final int maxIterations;
    private final List<AgentListener> listeners;
    @Nullable
    private final CheckpointStore checkpointStore;

    private AgentGraph(Builder b) {
        this.name = b.name;
        this.nodes = Map.copyOf(b.nodes);
        this.edges = List.copyOf(b.edges);
        this.entryNode = Objects.requireNonNull(b.entryNode,
                "entryNode must be set (first addNode is used by default)");
        this.errorPolicy = b.errorPolicy;
        this.maxIterations = b.maxIterations;
        this.listeners = List.copyOf(b.listeners);
        this.checkpointStore = b.checkpointStore;

        validate();
    }

    private void validate() {
        if (!nodes.containsKey(entryNode)) {
            throw new IllegalStateException("Entry node '" + entryNode + "' is not registered");
        }
        for (Edge edge : edges) {
            if (!nodes.containsKey(edge.from())) {
                throw new IllegalStateException("Edge from unknown node: " + edge.from());
            }
            if (!nodes.containsKey(edge.to())) {
                throw new IllegalStateException("Edge to unknown node: " + edge.to());
            }
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public String name() {
        return name;
    }

    public AgentResult invoke(AgentContext initial) {
        Objects.requireNonNull(initial, "initial");
        return run(initial, entryNode, 0, null);
    }

    public AgentResult invoke(AgentContext initial, String runId) {
        Objects.requireNonNull(initial, "initial");
        Objects.requireNonNull(runId, "runId");
        CheckpointStore store = requireCheckpointStore();
        store.save(new Checkpoint(runId, entryNode, initial, 0, null));
        return run(initial, entryNode, 0, runId);
    }

    public AgentResult resume(String runId, Message... additional) {
        Objects.requireNonNull(runId, "runId");
        CheckpointStore store = requireCheckpointStore();
        Checkpoint cp = store.load(runId)
                .orElseThrow(() -> new IllegalStateException(
                        "No checkpoint found for runId=" + runId));
        AgentContext context = cp.context();
        if (additional != null && additional.length > 0) {
            context = context.withMessages(List.of(additional));
        }
        return run(context, cp.nextNode(), cp.iterations(), runId);
    }

    private CheckpointStore requireCheckpointStore() {
        if (checkpointStore == null) {
            throw new IllegalStateException(
                    "AgentGraph has no CheckpointStore; configure one via Builder.checkpointStore(...)");
        }
        return checkpointStore;
    }

    private AgentResult run(AgentContext context, String startNode,
                            int startIterations, @Nullable String runId) {
        String currentNode = startNode;
        AgentResult lastResult = null;
        int iterations = startIterations;
        CheckpointStore store = checkpointStore;

        while (currentNode != null) {
            if (++iterations > maxIterations) {
                AgentError err = AgentError.of(currentNode,
                        new IllegalStateException("Max iterations exceeded: " + maxIterations));
                notifyError(currentNode, err);
                return AgentResult.failed(err);
            }

            Node node = nodes.get(currentNode);
            notifyEnter(currentNode, context);

            NodeOutcome outcome = executeWithPolicy(node, context);
            notifyExit(currentNode, outcome.result, outcome.durationNanos);

            if (outcome.result.hasError()) {
                notifyError(currentNode, outcome.result.error());
                if (errorPolicy == ErrorPolicy.FAIL_FAST) {
                    notifyGraphComplete(outcome.result);
                    return outcome.result;
                }
                lastResult = outcome.result;
            }
            else {
                context = context.applyResult(outcome.result);
                lastResult = outcome.result;
            }

            if (outcome.result.isInterrupted()) {
                if (runId != null && store != null) {
                    store.save(new Checkpoint(runId, currentNode, context,
                            iterations - 1, outcome.result.interrupt()));
                }
                notifyGraphComplete(outcome.result);
                return outcome.result;
            }

            String next = nextNode(currentNode, context, lastResult).orElse(null);

            if (runId != null && store != null) {
                if (next != null) {
                    store.save(new Checkpoint(runId, next, context, iterations, null));
                }
                else {
                    store.delete(runId);
                }
            }

            currentNode = next;
        }

        AgentResult finalResult = lastResult != null ? lastResult : AgentResult.ofText(null);
        notifyGraphComplete(finalResult);
        return finalResult;
    }

    public Flux<AgentEvent> invokeStream(AgentContext initial) {
        return Flux.create(sink -> {
            try {
                AgentContext context = initial;
                String currentNode = entryNode;
                AgentResult lastResult = null;
                String previousNode = null;
                int iterations = 0;

                while (currentNode != null) {
                    if (++iterations > maxIterations) {
                        AgentError err = AgentError.of(currentNode,
                                new IllegalStateException("Max iterations exceeded: " + maxIterations));
                        sink.next(AgentEvent.completed(AgentResult.failed(err)));
                        sink.complete();
                        return;
                    }

                    if (previousNode != null) {
                        sink.next(AgentEvent.transition(previousNode, currentNode));
                    }

                    Node node = nodes.get(currentNode);
                    notifyEnter(currentNode, context);

                    NodeOutcome outcome = streamNodeWithPolicy(node, context, sink);
                    notifyExit(currentNode, outcome.result, outcome.durationNanos);

                    if (outcome.result.hasError()) {
                        notifyError(currentNode, outcome.result.error());
                        if (errorPolicy == ErrorPolicy.FAIL_FAST) {
                            sink.next(AgentEvent.completed(outcome.result));
                            sink.complete();
                            return;
                        }
                        lastResult = outcome.result;
                    }
                    else {
                        context = context.applyResult(outcome.result);
                        lastResult = outcome.result;
                    }

                    previousNode = currentNode;
                    currentNode = nextNode(currentNode, context, lastResult).orElse(null);
                }

                AgentResult finalResult = lastResult != null ? lastResult : AgentResult.ofText(null);
                notifyGraphComplete(finalResult);
                sink.next(AgentEvent.completed(finalResult));
                sink.complete();
            }
            catch (Throwable t) {
                sink.error(t);
            }
        });
    }

    private NodeOutcome streamNodeWithPolicy(Node node, AgentContext context,
                                             reactor.core.publisher.FluxSink<AgentEvent> sink) {
        long start = System.nanoTime();
        AgentResult result = tryStream(node, context, sink, 0);

        if (result.hasError() && errorPolicy == ErrorPolicy.RETRY_ONCE) {
            log.warn("Node '{}' failed during stream, retrying once", node.name());
            result = tryStream(node, context, sink, 1);
        }
        long duration = System.nanoTime() - start;

        AgentError err = result.error();
        if (err != null && errorPolicy == ErrorPolicy.SKIP_NODE) {
            log.warn("Node '{}' failed during stream, skipping (policy=SKIP_NODE)",
                    node.name(), err.cause());
        }
        return new NodeOutcome(result, duration);
    }

    private AgentResult tryStream(Node node, AgentContext context,
                                  reactor.core.publisher.FluxSink<AgentEvent> sink, int retryCount) {
        java.util.concurrent.atomic.AtomicReference<AgentResult> holder =
                new java.util.concurrent.atomic.AtomicReference<>();
        try {
            for (AgentEvent event : node.executeStream(context).toIterable()) {
                if (event instanceof AgentEvent.Completed completed) {
                    holder.set(completed.result());
                }
                else {
                    sink.next(event);
                }
            }
        }
        catch (Throwable t) {
            return AgentResult.failed(new AgentError(node.name(), t, retryCount));
        }
        AgentResult result = holder.get();
        if (result == null) {
            return AgentResult.failed(new AgentError(node.name(),
                    new IllegalStateException("Node stream did not emit a Completed event"),
                    retryCount));
        }
        AgentError err = result.error();
        if (err != null) {
            return AgentResult.failed(err.withRetryCount(retryCount));
        }
        return result;
    }

    private NodeOutcome executeWithPolicy(Node node, AgentContext context) {
        long start = System.nanoTime();
        AgentResult result = tryExecute(node, context, 0);

        if (result.hasError() && errorPolicy == ErrorPolicy.RETRY_ONCE) {
            log.warn("Node '{}' failed, retrying once", node.name());
            result = tryExecute(node, context, 1);
        }
        long duration = System.nanoTime() - start;

        AgentError err = result.error();
        if (err != null && errorPolicy == ErrorPolicy.SKIP_NODE) {
            log.warn("Node '{}' failed, skipping (policy=SKIP_NODE)", node.name(), err.cause());
        }

        return new NodeOutcome(result, duration);
    }

    private AgentResult tryExecute(Node node, AgentContext context, int retryCount) {
        try {
            AgentResult result = node.execute(context);
            AgentError err = result.error();
            if (err != null) {
                return AgentResult.failed(err.withRetryCount(retryCount));
            }
            return result;
        }
        catch (Throwable t) {
            return AgentResult.failed(new AgentError(node.name(), t, retryCount));
        }
    }

    private Optional<String> nextNode(String from, AgentContext context, AgentResult lastResult) {
        String directFallback = null;
        for (Edge edge : edges) {
            if (!edge.from().equals(from)) {
                continue;
            }
            if (edge instanceof Edge.OnResult onResult
                    && lastResult != null
                    && onResult.matches(context, lastResult)) {
                return Optional.of(onResult.to());
            }
            if (edge instanceof Edge.Conditional cond && cond.matches(context)) {
                return Optional.of(cond.to());
            }
            if (edge instanceof Edge.Direct direct && directFallback == null) {
                directFallback = direct.to();
            }
        }
        return Optional.ofNullable(directFallback);
    }

    private void notifyEnter(String node, AgentContext context) {
        for (AgentListener l : listeners) {
            try { l.onNodeEnter(name, node, context); }
            catch (Exception e) { log.warn("Listener failed on enter", e); }
        }
    }

    private void notifyExit(String node, AgentResult result, long duration) {
        for (AgentListener l : listeners) {
            try { l.onNodeExit(name, node, result, duration); }
            catch (Exception e) { log.warn("Listener failed on exit", e); }
        }
    }

    private void notifyError(String node, AgentError error) {
        for (AgentListener l : listeners) {
            try { l.onNodeError(name, node, error); }
            catch (Exception e) { log.warn("Listener failed on error", e); }
        }
    }

    private void notifyGraphComplete(AgentResult result) {
        for (AgentListener l : listeners) {
            try { l.onGraphComplete(name, result); }
            catch (Exception e) { log.warn("Listener failed on graph complete", e); }
        }
    }

    private record NodeOutcome(AgentResult result, long durationNanos) {}

    public static final class Builder {
        private String name = "agent-graph";
        private final Map<String, Node> nodes = new LinkedHashMap<>();
        private final List<Edge> edges = new ArrayList<>();
        private String entryNode;
        private ErrorPolicy errorPolicy = ErrorPolicy.FAIL_FAST;
        private int maxIterations = 25;
        private final List<AgentListener> listeners = new ArrayList<>();
        @Nullable
        private CheckpointStore checkpointStore;

        public Builder name(String name) {
            this.name = Objects.requireNonNull(name, "name");
            return this;
        }

        public Builder addNode(String name, Agent agent) {
            return addNode(Node.of(name, agent));
        }

        public Builder addNode(Node node) {
            Objects.requireNonNull(node, "node");
            if (nodes.putIfAbsent(node.name(), node) != null) {
                throw new IllegalStateException("Duplicate node: " + node.name());
            }
            if (entryNode == null) {
                entryNode = node.name();
            }
            return this;
        }

        public Builder entryNode(String name) {
            this.entryNode = Objects.requireNonNull(name, "entryNode");
            return this;
        }

        public Builder addEdge(String from, String to) {
            edges.add(Edge.direct(from, to));
            return this;
        }

        public Builder addEdge(Edge edge) {
            edges.add(Objects.requireNonNull(edge, "edge"));
            return this;
        }

        public Builder errorPolicy(ErrorPolicy policy) {
            this.errorPolicy = Objects.requireNonNull(policy, "policy");
            return this;
        }

        public Builder maxIterations(int max) {
            if (max <= 0) {
                throw new IllegalArgumentException("maxIterations must be > 0");
            }
            this.maxIterations = max;
            return this;
        }

        public Builder listener(AgentListener listener) {
            listeners.add(Objects.requireNonNull(listener, "listener"));
            return this;
        }

        public Builder checkpointStore(CheckpointStore store) {
            this.checkpointStore = Objects.requireNonNull(store, "store");
            return this;
        }

        public AgentGraph build() {
            return new AgentGraph(this);
        }
    }
}
