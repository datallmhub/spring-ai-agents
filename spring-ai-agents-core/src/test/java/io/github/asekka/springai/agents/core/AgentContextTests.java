package io.github.asekka.springai.agents.core;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;

import static org.assertj.core.api.Assertions.assertThat;

class AgentContextTests {

    private static final StateKey<String> USER_ID = StateKey.of("userId", String.class);

    @Test
    void ofUserMessageSeedsContext() {
        AgentContext ctx = AgentContext.of("hello");
        assertThat(ctx.messages()).hasSize(1);
        assertThat(ctx.messages().get(0).getText()).isEqualTo("hello");
    }

    @Test
    void withReturnsNewInstance() {
        AgentContext original = AgentContext.empty();
        AgentContext updated = original.with(USER_ID, "u-1");

        assertThat(original.get(USER_ID)).isNull();
        assertThat(updated.get(USER_ID)).isEqualTo("u-1");
    }

    @Test
    void withMessageAppends() {
        AgentContext ctx = AgentContext.of("hello")
                .withMessage(new AssistantMessage("hi"));
        assertThat(ctx.messages()).hasSize(2);
    }

    @Test
    void applyResultMergesStateUpdates() {
        AgentContext ctx = AgentContext.empty();
        AgentResult result = AgentResult.builder()
                .stateUpdates(Map.of(USER_ID, "u-42"))
                .build();
        AgentContext next = ctx.applyResult(result);

        assertThat(next.get(USER_ID)).isEqualTo("u-42");
    }
}
