package io.github.asekka.springai.agents.autoconfigure;

import java.util.concurrent.TimeUnit;

import io.github.asekka.springai.agents.core.AgentError;
import io.github.asekka.springai.agents.core.AgentResult;
import io.github.asekka.springai.agents.graph.AgentListener;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

public final class MicrometerAgentListener implements AgentListener {

    private final MeterRegistry registry;

    public MicrometerAgentListener(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void onNodeExit(String graphName, String nodeName, AgentResult result, long durationNanos) {
        String status = result.hasError() ? "error" : "success";
        registry.counter("agents.execution.count",
                Tags.of("agent", nodeName, "graph", graphName, "status", status))
                .increment();
        Timer.builder("agents.execution.duration")
                .tags("agent", nodeName, "graph", graphName)
                .register(registry)
                .record(durationNanos, TimeUnit.NANOSECONDS);
    }

    @Override
    public void onTransition(String graphName, String from, String to) {
        registry.counter("agents.graph.transitions",
                Tags.of("graph", graphName, "from", from, "to", to))
                .increment();
    }

    @Override
    public void onNodeError(String graphName, String nodeName, AgentError error) {
        registry.counter("agents.execution.errors",
                Tags.of("agent", nodeName, "graph", graphName,
                        "cause", error.cause().getClass().getSimpleName()))
                .increment();
    }
}
