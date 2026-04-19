package io.github.asekka.springai.agents.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AgentResultTests {

    @Test
    void ofTextBuildsCompletedResult() {
        AgentResult result = AgentResult.ofText("done");
        assertThat(result.text()).isEqualTo("done");
        assertThat(result.completed()).isTrue();
        assertThat(result.hasError()).isFalse();
        assertThat(result.toolCalls()).isEmpty();
    }

    @Test
    void failedResultCarriesError() {
        AgentError err = AgentError.of("node-a", new RuntimeException("boom"));
        AgentResult result = AgentResult.failed(err);

        assertThat(result.completed()).isFalse();
        assertThat(result.hasError()).isTrue();
        assertThat(result.error()).isEqualTo(err);
    }

    @Test
    void toolCallsListIsImmutable() {
        AgentResult result = AgentResult.builder().build();
        assertThat(result.toolCalls()).isUnmodifiable();
        assertThat(result.stateUpdates()).isUnmodifiable();
    }
}
