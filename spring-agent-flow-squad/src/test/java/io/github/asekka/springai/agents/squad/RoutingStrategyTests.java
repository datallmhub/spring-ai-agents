package io.github.asekka.springai.agents.squad;

import java.util.LinkedHashSet;
import java.util.Set;

import io.github.asekka.springai.agents.core.AgentContext;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.ai.chat.client.ChatClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RoutingStrategyTests {

    @Test
    void firstThrowsOnEmpty() {
        assertThatThrownBy(() ->
                RoutingStrategy.first().selectExecutor(AgentContext.empty(), Set.of()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void fixedThrowsOnMissing() {
        assertThatThrownBy(() ->
                RoutingStrategy.fixed("ghost").selectExecutor(AgentContext.empty(), Set.of("a")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void markerSentinelThrowsOnUse() {
        assertThatThrownBy(() ->
                RoutingStrategy.LLM_DRIVEN_MARKER.selectExecutor(AgentContext.empty(), Set.of("a")))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void llmDrivenPicksMatchingExecutorName() {
        ChatClient chatClient = mock(ChatClient.class, Answers.RETURNS_DEEP_STUBS);
        when(chatClient.prompt().system(anyString()).user(anyString())
                .call().content()).thenReturn("research");

        RoutingStrategy strategy = RoutingStrategy.llmDriven(chatClient);
        String chosen = strategy.selectExecutor(
                AgentContext.of("please research X"),
                Set.of("research", "writer"));

        assertThat(chosen).isEqualTo("research");
    }

    @Test
    void llmDrivenFallsBackToFirstOnUnknownAnswer() {
        ChatClient chatClient = mock(ChatClient.class, Answers.RETURNS_DEEP_STUBS);
        when(chatClient.prompt().system(anyString()).user(anyString())
                .call().content()).thenReturn("completely-unrelated");

        RoutingStrategy strategy = RoutingStrategy.llmDriven(chatClient);
        LinkedHashSet<String> ordered = new LinkedHashSet<>();
        ordered.add("first-option");
        ordered.add("second-option");
        String chosen = strategy.selectExecutor(AgentContext.of("hi"), ordered);

        assertThat(chosen).isEqualTo("first-option");
    }

    @Test
    void llmDrivenMatchesSubstring() {
        ChatClient chatClient = mock(ChatClient.class, Answers.RETURNS_DEEP_STUBS);
        when(chatClient.prompt().system(anyString()).user(anyString())
                .call().content()).thenReturn("I think writer is best");

        RoutingStrategy strategy = RoutingStrategy.llmDriven(chatClient);
        String chosen = strategy.selectExecutor(
                AgentContext.of("hi"),
                Set.of("research", "writer"));

        assertThat(chosen).isEqualTo("writer");
    }

    @Test
    void llmDrivenThrowsWhenNoExecutors() {
        ChatClient chatClient = mock(ChatClient.class, Answers.RETURNS_DEEP_STUBS);
        RoutingStrategy strategy = RoutingStrategy.llmDriven(chatClient);
        assertThatThrownBy(() ->
                strategy.selectExecutor(AgentContext.empty(), Set.of()))
                .isInstanceOf(IllegalStateException.class);
    }
}
