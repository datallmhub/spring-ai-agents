package io.github.asekka.springai.agents.squad;

import java.util.concurrent.atomic.AtomicInteger;

import io.github.asekka.springai.agents.core.Agent;
import io.github.asekka.springai.agents.core.AgentContext;
import io.github.asekka.springai.agents.core.AgentResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SupervisorLoopCompositionTests {

    @Test
    void reActWrappedCoordinatorRoutesUntilCompleted() {
        AtomicInteger round = new AtomicInteger();

        Agent researcher = ctx -> AgentResult.builder()
                .text("research round " + round.incrementAndGet())
                .completed(false)
                .build();

        Agent writer = ctx -> AgentResult.ofText("final answer");

        RoutingStrategy alternating = (ctx, executors) ->
                round.get() < 2 ? "research" : "write";

        CoordinatorAgent coordinator = CoordinatorAgent.builder()
                .executor("research", researcher)
                .executor("write", writer)
                .routingStrategy(alternating)
                .build();

        ReActAgent supervisor = ReActAgent.builder()
                .inner(coordinator)
                .maxSteps(5)
                .build();

        AgentResult result = supervisor.execute(AgentContext.of("investigate"));

        assertThat(result.text()).isEqualTo("final answer");
        assertThat(round.get()).isEqualTo(2);
    }
}
