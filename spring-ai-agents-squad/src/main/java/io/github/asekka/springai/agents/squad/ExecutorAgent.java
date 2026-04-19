package io.github.asekka.springai.agents.squad;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.github.asekka.springai.agents.core.Agent;
import io.github.asekka.springai.agents.core.AgentContext;
import io.github.asekka.springai.agents.core.AgentEvent;
import io.github.asekka.springai.agents.core.AgentResult;
import io.github.asekka.springai.agents.core.AgentUsage;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.lang.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public final class ExecutorAgent implements Agent {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ExecutorAgent.class);

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
        log.info("executor.run: executor={}", name);
        try {
            ChatResponse response = buildSpec(context).call().chatResponse();
            String content = response != null && response.getResult() != null
                    && response.getResult().getOutput() != null
                            ? response.getResult().getOutput().getText()
                            : null;
            return AgentResult.builder()
                    .text(content)
                    .completed(true)
                    .usage(extractUsage(response))
                    .build();
        } catch (Throwable t) {
            log.error("executor.failure: executor={}", name, t);
            throw t;
        }
    }

    @Nullable
    private static AgentUsage extractUsage(@Nullable ChatResponse response) {
        if (response == null || response.getMetadata() == null) {
            return null;
        }
        Usage usage = response.getMetadata().getUsage();
        if (usage == null) {
            return null;
        }
        Integer prompt = usage.getPromptTokens();
        Integer completion = usage.getCompletionTokens();
        Integer total = usage.getTotalTokens();
        long p = prompt == null ? 0L : prompt.longValue();
        long c = completion == null ? 0L : completion.longValue();
        long t = total == null ? p + c : total.longValue();
        return new AgentUsage(p, c, t);
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
            spec = spec.messages(ensureSafeMessageOrder(context.messages()));
        }
        if (!tools.isEmpty()) {
            spec = spec.toolCallbacks(tools.toArray(new ToolCallback[0]));
        }
        return spec;
    }

    private List<org.springframework.ai.chat.messages.Message> ensureSafeMessageOrder(
            List<org.springframework.ai.chat.messages.Message> messages) {
        if (messages.isEmpty()) {
            return messages;
        }
        org.springframework.ai.chat.messages.Message last = messages.get(messages.size() - 1);
        if (last instanceof org.springframework.ai.chat.messages.AssistantMessage) {
            List<org.springframework.ai.chat.messages.Message> safe = new ArrayList<>(messages);
            safe.add(new org.springframework.ai.chat.messages.UserMessage("Proceed."));
            return safe;
        }
        return messages;
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
