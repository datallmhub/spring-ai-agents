package io.github.asekka.springai.agents.autoconfigure;

import io.github.asekka.springai.agents.core.AgentContext;
import io.github.asekka.springai.agents.core.AgentResult;
import io.github.asekka.springai.agents.graph.AgentGraph;
import io.github.asekka.springai.agents.graph.AgentListener;
import io.github.asekka.springai.agents.squad.RoutingStrategy;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full Spring Boot integration test: loads a real ApplicationContext
 * via @SpringBootTest and verifies end-to-end wiring of the starter.
 *
 * <p>No real LLM is required — agents are lightweight lambdas.
 * The test validates that:
 * <ul>
 *   <li>The auto-configuration boots without errors</li>
 *   <li>{@code RoutingStrategy} and {@code AgentListener} are wired as beans</li>
 *   <li>An {@code AgentGraph} built inside the context executes correctly</li>
 *   <li>Micrometer metrics are recorded after graph execution</li>
 * </ul>
 */
@SpringBootTest(classes = SpringAiAgentsIntegrationTest.TestConfig.class)
class SpringAiAgentsIntegrationTest {

    @Autowired
    ApplicationContext ctx;

    @Autowired
    MeterRegistry meterRegistry;

    @Autowired
    AgentListener agentListener;

    @Autowired
    RoutingStrategy routingStrategy;

    @Test
    void contextLoadsAndBeansAreWired() {
        assertThat(ctx).isNotNull();
        assertThat(agentListener).isNotNull();
        assertThat(routingStrategy).isNotNull();
    }

    @Test
    void agentGraphRunsEndToEndInsideSpringContext() {
        AgentGraph graph = AgentGraph.builder()
                .name("integration-test-graph")
                .addNode("step1", c -> AgentResult.ofText("hello"))
                .addNode("step2", c -> AgentResult.ofText("world"))
                .addEdge("step1", "step2")
                .listener(agentListener)
                .build();

        AgentResult result = graph.invoke(AgentContext.of("integration test"));

        assertThat(result.text()).isEqualTo("world");
        assertThat(result.hasError()).isFalse();
    }

    @Test
    void micrometerMetricsAreRecordedAfterExecution() {
        AgentGraph graph = AgentGraph.builder()
                .name("metrics-test-graph")
                .addNode("only", c -> AgentResult.ofText("done"))
                .listener(agentListener)
                .build();

        graph.invoke(AgentContext.of("measure me"));

        // MicrometerAgentListener records agents.execution.count
        assertThat(meterRegistry.find("agents.execution.count").counters())
                .isNotEmpty();
    }

    @Configuration
    @ImportAutoConfiguration(SpringAiAgentsAutoConfiguration.class)
    static class TestConfig {

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }
}
