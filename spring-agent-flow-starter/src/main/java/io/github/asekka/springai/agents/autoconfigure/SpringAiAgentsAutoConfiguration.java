package io.github.asekka.springai.agents.autoconfigure;

import io.github.asekka.springai.agents.graph.AgentListener;
import io.github.asekka.springai.agents.squad.RoutingStrategy;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnProperty(prefix = "spring.ai.agents", name = "enabled", havingValue = "true",
        matchIfMissing = true)
@EnableConfigurationProperties(SpringAiAgentsProperties.class)
public class SpringAiAgentsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    RoutingStrategy defaultRoutingStrategy(SpringAiAgentsProperties properties,
                                           org.springframework.beans.factory.ObjectProvider<ChatClient> chatClientProvider) {
        SpringAiAgentsProperties.Squad.DefaultRoutingStrategy choice =
                properties.getSquad().getDefaultRoutingStrategy();
        if (choice == SpringAiAgentsProperties.Squad.DefaultRoutingStrategy.LLM_DRIVEN) {
            ChatClient client = chatClientProvider.getIfAvailable();
            if (client != null) {
                return RoutingStrategy.llmDriven(client);
            }
        }
        return RoutingStrategy.first();
    }

    @Bean
    @ConditionalOnClass(MeterRegistry.class)
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "spring.ai.agents.observability", name = "metrics",
            havingValue = "true", matchIfMissing = true)
    AgentListener micrometerAgentListener(MeterRegistry registry) {
        return new MicrometerAgentListener(registry);
    }
}
