package io.github.asekka.springai.agents.squad;

import java.util.Set;

import io.github.asekka.springai.agents.core.Agent;
import io.github.asekka.springai.agents.core.AgentContext;
import org.springframework.ai.chat.client.ChatClient;

@FunctionalInterface
public interface RoutingStrategy {

    String selectExecutor(AgentContext context, Set<String> availableExecutors);

    static RoutingStrategy first() {
        return (ctx, names) -> {
            if (names.isEmpty()) {
                throw new IllegalStateException("No executors available for routing");
            }
            return names.iterator().next();
        };
    }

    static RoutingStrategy fixed(String executorName) {
        return (ctx, names) -> {
            if (!names.contains(executorName)) {
                throw new IllegalStateException("Fixed executor not registered: " + executorName);
            }
            return executorName;
        };
    }

    static RoutingStrategy llmDriven(ChatClient chatClient) {
        return new LlmRoutingStrategy(chatClient);
    }

    /**
     * Marker for {@link Agent}s that already embed their own routing logic
     * and should not be wrapped in a default strategy.
     */
    RoutingStrategy LLM_DRIVEN_MARKER = (ctx, names) -> {
        throw new IllegalStateException(
                "LLM_DRIVEN_MARKER is a sentinel; use RoutingStrategy.llmDriven(ChatClient) instead");
    };
}
