package io.github.asekka.springai.agents.squad;

import java.util.Locale;
import java.util.Set;

import io.github.asekka.springai.agents.core.AgentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;

final class LlmRoutingStrategy implements RoutingStrategy {

    private static final Logger log = LoggerFactory.getLogger(LlmRoutingStrategy.class);

    private final ChatClient chatClient;

    LlmRoutingStrategy(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public String selectExecutor(AgentContext context, Set<String> availableExecutors) {
        if (availableExecutors.isEmpty()) {
            throw new IllegalStateException("No executors available for routing");
        }

        String routingSystem = """
                You are a routing assistant. Given a user request and a list of available
                executors, reply with exactly one executor name (no quotes, no prose).
                Available executors: %s
                """.formatted(String.join(", ", availableExecutors));

        String userText = context.messages().stream()
                .map(Message::getText)
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");

        String answer = chatClient.prompt()
                .system(routingSystem)
                .user(userText)
                .call()
                .content();

        String chosen = normalize(answer, availableExecutors);
        if (chosen == null) {
            log.warn("LLM routing returned '{}', falling back to first executor", answer);
            return availableExecutors.iterator().next();
        }
        return chosen;
    }

    private static String normalize(String raw, Set<String> candidates) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.strip().toLowerCase(Locale.ROOT);
        for (String c : candidates) {
            if (cleaned.equals(c.toLowerCase(Locale.ROOT))) {
                return c;
            }
        }
        for (String c : candidates) {
            if (cleaned.contains(c.toLowerCase(Locale.ROOT))) {
                return c;
            }
        }
        return null;
    }
}
