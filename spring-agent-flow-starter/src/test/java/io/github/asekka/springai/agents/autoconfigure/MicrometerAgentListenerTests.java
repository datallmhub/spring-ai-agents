package io.github.asekka.springai.agents.autoconfigure;

import io.github.asekka.springai.agents.core.AgentError;
import io.github.asekka.springai.agents.core.AgentResult;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MicrometerAgentListenerTests {

    @Test
    void onNodeExitSuccessIncrementsCounterAndRecordsTimer() {
        MeterRegistry registry = new SimpleMeterRegistry();
        MicrometerAgentListener listener = new MicrometerAgentListener(registry);

        listener.onNodeExit("graph-a", "node-x", AgentResult.ofText("ok"), 1_000_000L);

        assertThat(registry.counter("agents.execution.count",
                "agent", "node-x", "graph", "graph-a", "status", "success").count())
                .isEqualTo(1.0);
        assertThat(registry.timer("agents.execution.duration",
                "agent", "node-x", "graph", "graph-a").count())
                .isEqualTo(1L);
    }

    @Test
    void onNodeExitFailureTagsStatusError() {
        MeterRegistry registry = new SimpleMeterRegistry();
        MicrometerAgentListener listener = new MicrometerAgentListener(registry);

        AgentError err = AgentError.of("node-x", new RuntimeException("x"));
        listener.onNodeExit("graph-a", "node-x", AgentResult.failed(err), 1_000L);

        assertThat(registry.counter("agents.execution.count",
                "agent", "node-x", "graph", "graph-a", "status", "error").count())
                .isEqualTo(1.0);
    }

    @Test
    void onNodeErrorIncrementsErrorsCounter() {
        MeterRegistry registry = new SimpleMeterRegistry();
        MicrometerAgentListener listener = new MicrometerAgentListener(registry);

        listener.onNodeError("graph-a", "node-x",
                AgentError.of("node-x", new IllegalArgumentException("bad")));

        assertThat(registry.counter("agents.execution.errors",
                "agent", "node-x", "graph", "graph-a", "cause", "IllegalArgumentException")
                .count())
                .isEqualTo(1.0);
    }
}
