package io.github.asekka.springai.agents.autoconfigure;

import io.github.asekka.springai.agents.graph.AgentListener;
import io.github.asekka.springai.agents.graph.ErrorPolicy;
import io.github.asekka.springai.agents.squad.RoutingStrategy;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SpringAiAgentsAutoConfigurationTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SpringAiAgentsAutoConfiguration.class));

    @Test
    void registersFirstRoutingStrategyByDefault() {
        runner.run(ctx -> {
            assertThat(ctx).hasSingleBean(RoutingStrategy.class);
        });
    }

    @Test
    void disabledWhenPropertyFalse() {
        runner.withPropertyValues("spring.ai.agents.enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(RoutingStrategy.class));
    }

    @Test
    void micrometerListenerRegisteredWhenRegistryPresent() {
        runner.withUserConfiguration(RegistryConfig.class)
                .run(ctx -> assertThat(ctx).hasSingleBean(AgentListener.class));
    }

    @Test
    void micrometerListenerDisabledByProperty() {
        runner.withUserConfiguration(RegistryConfig.class)
                .withPropertyValues("spring.ai.agents.observability.metrics=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(AgentListener.class));
    }

    @Test
    void llmDrivenRoutingUsesChatClientWhenAvailable() {
        runner.withUserConfiguration(ChatClientConfig.class)
                .withPropertyValues("spring.ai.agents.squad.default-routing-strategy=LLM_DRIVEN")
                .run(ctx -> assertThat(ctx).hasSingleBean(RoutingStrategy.class));
    }

    @Test
    void llmDrivenFallsBackToFirstWhenNoChatClient() {
        runner.withPropertyValues("spring.ai.agents.squad.default-routing-strategy=LLM_DRIVEN")
                .run(ctx -> assertThat(ctx).hasSingleBean(RoutingStrategy.class));
    }

    @Test
    void propertiesGettersAndSettersCoverAllFields() {
        SpringAiAgentsProperties props = new SpringAiAgentsProperties();
        props.setEnabled(false);
        props.setDefaultErrorPolicy(ErrorPolicy.SKIP_NODE);
        props.getObservability().setMetrics(false);
        props.getObservability().setEvents(false);
        props.getSquad().setDefaultRoutingStrategy(
                SpringAiAgentsProperties.Squad.DefaultRoutingStrategy.LLM_DRIVEN);

        assertThat(props.isEnabled()).isFalse();
        assertThat(props.getDefaultErrorPolicy()).isEqualTo(ErrorPolicy.SKIP_NODE);
        assertThat(props.getObservability().isMetrics()).isFalse();
        assertThat(props.getObservability().isEvents()).isFalse();
        assertThat(props.getSquad().getDefaultRoutingStrategy())
                .isEqualTo(SpringAiAgentsProperties.Squad.DefaultRoutingStrategy.LLM_DRIVEN);
    }

    @Configuration
    static class RegistryConfig {
        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }
    }

    @Configuration
    static class ChatClientConfig {
        @Bean
        ChatClient chatClient() {
            return mock(ChatClient.class, Answers.RETURNS_DEEP_STUBS);
        }
    }
}
