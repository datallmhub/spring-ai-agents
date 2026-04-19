package io.github.asekka.springai.agents.squad;

import io.github.asekka.springai.agents.core.AgentContext;
import io.github.asekka.springai.agents.core.AgentEvent;
import io.github.asekka.springai.agents.core.AgentResult;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ExecutorAgentTests {

    @Test
    void executeCallsChatClientAndReturnsText() {
        ChatClient chatClient = mock(ChatClient.class, Answers.RETURNS_DEEP_STUBS);
        when(chatClient.prompt().system(anyString())
                .messages(any(java.util.List.class))
                .call().content()).thenReturn("answer");

        ExecutorAgent agent = ExecutorAgent.builder()
                .name("exec")
                .chatClient(chatClient)
                .systemPrompt("you are helpful")
                .build();

        AgentResult result = agent.execute(AgentContext.of("question"));
        assertThat(result.text()).isEqualTo("answer");
    }

    @Test
    void executeSkipsSystemPromptWhenBlank() {
        ChatClient chatClient = mock(ChatClient.class, Answers.RETURNS_DEEP_STUBS);
        when(chatClient.prompt()
                .messages(any(java.util.List.class))
                .call().content()).thenReturn("answer");

        ExecutorAgent agent = ExecutorAgent.builder()
                .chatClient(chatClient)
                .systemPrompt("")
                .build();

        assertThat(agent.execute(AgentContext.of("q")).text()).isEqualTo("answer");
    }

    @Test
    void executeStreamForwardsTokensAndEmitsCompletion() {
        ChatClient chatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec spec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.StreamResponseSpec streamSpec = mock(ChatClient.StreamResponseSpec.class);
        when(chatClient.prompt()).thenReturn(spec);
        when(spec.system(anyString())).thenReturn(spec);
        when(spec.messages(any(java.util.List.class))).thenReturn(spec);
        when(spec.stream()).thenReturn(streamSpec);
        when(streamSpec.content()).thenReturn(Flux.just("hel", "lo"));

        ExecutorAgent agent = ExecutorAgent.builder()
                .chatClient(chatClient)
                .systemPrompt("s")
                .build();

        StepVerifier.create(agent.executeStream(AgentContext.of("q")))
                .expectNextMatches(e -> e instanceof AgentEvent.Token t && "hel".equals(t.chunk()))
                .expectNextMatches(e -> e instanceof AgentEvent.Token t && "lo".equals(t.chunk()))
                .expectNextMatches(e -> e instanceof AgentEvent.Completed c
                        && "hello".equals(c.result().text()))
                .verifyComplete();
    }

    @Test
    void builderRejectsNullChatClient() {
        assertThatThrownBy(() -> ExecutorAgent.builder().build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void builderAcceptsVarargToolsAndListTools() {
        ChatClient chatClient = mock(ChatClient.class, Answers.RETURNS_DEEP_STUBS);
        ExecutorAgent agent = ExecutorAgent.builder()
                .chatClient(chatClient)
                .tools() // varargs zero
                .tools(java.util.List.of())
                .build();
        assertThat(agent.name()).isEqualTo("executor");
    }
}
