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

    @Test
    void usageFieldCarriesTokenCounts() {
        AgentResult r = AgentResult.builder()
                .text("ok")
                .usage(AgentUsage.of(10, 5))
                .build();
        assertThat(r.usage()).isEqualTo(new AgentUsage(10, 5, 15));
    }

    @Test
    void usageFieldDefaultsToNull() {
        assertThat(AgentResult.ofText("hi").usage()).isNull();
    }

    @Test
    void interruptedFactoryFromReasonAndFromRequest() {
        AgentResult r1 = AgentResult.interrupted("need confirmation");
        assertThat(r1.isInterrupted()).isTrue();
        assertThat(r1.interrupt().reason()).isEqualTo("need confirmation");
        assertThat(r1.interrupt().payload()).isNull();

        InterruptRequest req = new InterruptRequest("ask", "payload-value");
        AgentResult r2 = AgentResult.interrupted(req);
        assertThat(r2.interrupt()).isEqualTo(req);
        assertThat(r2.interrupt().payload()).isEqualTo("payload-value");
    }

    @Test
    void builderInterruptSetters() {
        AgentResult viaString = AgentResult.builder().interrupt("need input").build();
        assertThat(viaString.isInterrupted()).isTrue();

        InterruptRequest req = InterruptRequest.of("reason");
        AgentResult viaRequest = AgentResult.builder().interrupt(req).build();
        assertThat(viaRequest.interrupt()).isEqualTo(req);
    }

    @Test
    void hasToolCallsReturnsFalseByDefault() {
        assertThat(AgentResult.ofText("x").hasToolCalls()).isFalse();
    }
}
