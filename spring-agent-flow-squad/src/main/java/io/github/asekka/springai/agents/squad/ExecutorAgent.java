package io.github.asekka.springai.agents.squad;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.github.asekka.springai.agents.core.Agent;
import io.github.asekka.springai.agents.core.AgentContext;
import io.github.asekka.springai.agents.core.AgentEvent;
import io.github.asekka.springai.agents.core.AgentResult;
import io.github.asekka.springai.agents.core.AgentUsage;
import io.github.asekka.springai.agents.core.StateKey;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ResponseEntity;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.tool.ToolCallback;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

public final class ExecutorAgent implements Agent {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ExecutorAgent.class);

    private final String name;
    private final ChatClient chatClient;
    private final String systemPrompt;
    private final List<ToolCallback> tools;
    @Nullable private final StateKey<?> outputKey;

    private ExecutorAgent(Builder b) {
        this.name = b.name;
        this.chatClient = Objects.requireNonNull(b.chatClient, "chatClient");
        this.systemPrompt = b.systemPrompt;
        this.tools = List.copyOf(b.tools);
        this.outputKey = b.outputKey;
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
        ToolCallCollector collector = new ToolCallCollector();
        try {
            ChatClient.CallResponseSpec call = buildSpec(context, collector).call();
            ChatResponse response;
            Object entity;
            if (outputKey != null) {
                ResponseEntity<ChatResponse, ?> typed = call.responseEntity(outputKey.type());
                response = typed.getResponse();
                entity = typed.getEntity();
            } else {
                response = call.chatResponse();
                entity = null;
            }
            String content = response != null && response.getResult() != null
                    && response.getResult().getOutput() != null
                            ? response.getResult().getOutput().getText()
                            : null;
            AgentResult.Builder rb = AgentResult.builder()
                    .text(content)
                    .toolCalls(collector.snapshot())
                    .completed(true)
                    .usage(extractUsage(response));
            if (entity != null) {
                rb.structuredOutput(entity).stateUpdates(stateUpdateFor(entity));
            }
            return rb.build();
        } catch (Throwable t) {
            log.error("executor.failure: executor={}", name, t);
            throw t;
        }
    }

    private Map<StateKey<?>, Object> stateUpdateFor(Object entity) {
        return outputKey == null ? Map.of() : Map.of(outputKey, entity);
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
        Sinks.Many<AgentEvent> toolEvents = Sinks.many().multicast().onBackpressureBuffer();
        ToolCallCollector collector = new ToolCallCollector(evt -> toolEvents.tryEmitNext(evt));
        Flux<AgentEvent> tokens = buildSpec(context, collector).stream()
                .content()
                .map(chunk -> {
                    buffer.append(chunk);
                    return (AgentEvent) AgentEvent.token(chunk);
                })
                .doOnTerminate(toolEvents::tryEmitComplete);
        Mono<AgentEvent> completion = Mono.fromSupplier(() -> AgentEvent.completed(
                AgentResult.builder()
                        .text(buffer.toString())
                        .toolCalls(collector.snapshot())
                        .completed(true)
                        .build()));
        return Flux.merge(tokens, toolEvents.asFlux()).concatWith(completion);
    }

    private ChatClient.ChatClientRequestSpec buildSpec(AgentContext context, ToolCallCollector collector) {
        ChatClient.ChatClientRequestSpec spec = chatClient.prompt();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            spec = spec.system(systemPrompt);
        }
        if (!context.messages().isEmpty()) {
            spec = spec.messages(ensureSafeMessageOrder(context.messages()));
        }
        if (!tools.isEmpty()) {
            ToolCallback[] wrapped = new ToolCallback[tools.size()];
            for (int i = 0; i < tools.size(); i++) {
                wrapped[i] = new RecordingToolCallback(tools.get(i), collector);
            }
            spec = spec.toolCallbacks(wrapped);
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
        @Nullable private StateKey<?> outputKey;

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

        public Builder outputKey(StateKey<?> outputKey) {
            this.outputKey = outputKey;
            return this;
        }

        public ExecutorAgent build() {
            return new ExecutorAgent(this);
        }
    }
}
