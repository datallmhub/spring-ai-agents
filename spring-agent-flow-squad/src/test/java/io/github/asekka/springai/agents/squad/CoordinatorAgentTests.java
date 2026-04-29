package io.github.asekka.springai.agents.squad;

import io.github.asekka.springai.agents.core.Agent;
import io.github.asekka.springai.agents.core.AgentContext;
import io.github.asekka.springai.agents.core.AgentResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CoordinatorAgentTests {

    private static Agent returning(String text) {
        return ctx -> AgentResult.ofText(text);
    }

    @Test
    void routesToFirstExecutorByDefault() {
        CoordinatorAgent coord = CoordinatorAgent.builder()
                .executor("alpha", returning("A"))
                .executor("beta", returning("B"))
                .build();

        AgentResult result = coord.execute(AgentContext.empty());
        assertThat(result.text()).isEqualTo("A");
    }

    @Test
    void fixedStrategyRoutesToNamedExecutor() {
        CoordinatorAgent coord = CoordinatorAgent.builder()
                .executor("alpha", returning("A"))
                .executor("beta", returning("B"))
                .routingStrategy(RoutingStrategy.fixed("beta"))
                .build();

        AgentResult result = coord.execute(AgentContext.empty());
        assertThat(result.text()).isEqualTo("B");
    }

    @Test
    void fixedStrategyRejectsUnknownExecutor() {
        CoordinatorAgent coord = CoordinatorAgent.builder()
                .executor("alpha", returning("A"))
                .routingStrategy(RoutingStrategy.fixed("ghost"))
                .build();

        AgentResult result = coord.execute(AgentContext.empty());
        assertThat(result.hasError()).isTrue();
    }

    @Test
    void executorFailureIsCapturedAsAgentError() {
        Agent failing = ctx -> { throw new RuntimeException("boom"); };

        CoordinatorAgent coord = CoordinatorAgent.builder()
                .executor("failer", failing)
                .build();

        AgentResult result = coord.execute(AgentContext.empty());
        assertThat(result.hasError()).isTrue();
        assertThat(result.error().nodeName()).isEqualTo("failer");
    }

    @Test
    void builderRejectsEmptyExecutors() {
        assertThatThrownBy(() -> CoordinatorAgent.builder().build())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least one executor");
    }
}
