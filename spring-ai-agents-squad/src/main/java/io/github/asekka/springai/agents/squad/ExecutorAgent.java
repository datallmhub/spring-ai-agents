package io.github.asekka.springai.agents.squad;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.github.asekka.springai.agents.core.Agent;
import io.github.asekka.springai.agents.core.AgentContext;
import io.github.asekka.springai.agents.core.AgentEvent;
import io.github.asekka.springai.agents.core.AgentResult;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallback;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public final class ExecutorAgent implements Agent {

    private final String name;
    private final ChatClient chatClient;
    private final String systemPrompt;
    private final List<ToolCallback> tools;

    private ExecutorAgent(Builder b) {
        this.name = b.name;
        this.chatClient = Objects.requireNonNull(b.chatClient, "chatClient");
        this.systemPrompt = b.systemPrompt;
        this.tools = List.copyOf(b.tools);
    }

    public static Builder builder() {
        return new Builder();
    }

    public String name() {
        return name;
    }

    @Override
    public AgentResult execute(AgentContext context) {
        String content = buildSpec(context).call().content();
        return AgentResult.ofText(content);
    }

    @Override
    public Flux<AgentEvent> executeStream(AgentContext context) {
        StringBuilder buffer = new StringBuilder();
        Flux<AgentEvent> tokens = buildSpec(context).stream()
                .content()
                .map(chunk -> {
                    buffer.append(chunk);
                    return (AgentEvent) AgentEvent.token(chunk);
                });
        Mono<AgentEvent> completion = Mono.fromSupplier(
                () -> AgentEvent.completed(AgentResult.ofText(buffer.toString())));
        return tokens.concatWith(completion);
    }

    private ChatClient.ChatClientRequestSpec buildSpec(AgentContext context) {
        ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            spec = spec.system(systemPrompt);
        }
        if (!context.messages().isEmpty()) {
            spec = spec.messages(context.messages());
        }
        if (!tools.isEmpty()) {
            spec = spec.toolCallbacks(tools.toArray(new ToolCallback[0]));
        }
        return spec;
    }

    public static final class Builder {
        private String name = "executor";
        private ChatClient chatClient;
        private String systemPrompt;
        private final List<ToolCallback> tools = new ArrayList<>();

        public Builder name(String name) {
            this.name = Objects.requireNonNull(name, "name");
            return this;
        }

        public Builder chatClient(ChatClient chatClient) {
            this.chatClient = chatClient;
            return this;
        }

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public Builder tools(ToolCallback... tools) {
            if (tools != null) {
                for (ToolCallback t : tools) {
                    this.tools.add(Objects.requireNonNull(t, "tool"));
                }
            }
            return this;
        }

        public Builder tools(List<ToolCallback> tools) {
            if (tools != null) {
                tools.forEach(t -> this.tools.add(Objects.requireNonNull(t, "tool")));
            }
            return this;
        }

        public ExecutorAgent build() {
            return new ExecutorAgent(this);
        }
    }
}
