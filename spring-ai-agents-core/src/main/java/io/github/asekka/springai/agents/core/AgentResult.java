package io.github.asekka.springai.agents.core;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.ai.chat.messages.AssistantMessage.ToolCall;
import org.springframework.lang.Nullable;

public record AgentResult(
        @Nullable String text,
        List<ToolCall> toolCalls,
        @Nullable Object structuredOutput,
        Map<StateKey<?>, Object> stateUpdates,
        boolean completed,
        @Nullable AgentError error) {

    public AgentResult {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        stateUpdates = stateUpdates == null ? Map.of() : Map.copyOf(stateUpdates);
    }

    public static AgentResult ofText(String text) {
        return new Builder().text(text).completed(true).build();
    }

    public static AgentResult failed(AgentError error) {
        Objects.requireNonNull(error, "error");
        return new Builder().error(error).completed(false).build();
    }

    public boolean hasError() {
        return error != null;
    }

    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String text;
        private List<ToolCall> toolCalls = List.of();
        private Object structuredOutput;
        private Map<StateKey<?>, Object> stateUpdates = Map.of();
        private boolean completed = true;
        private AgentError error;

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder toolCalls(List<ToolCall> toolCalls) {
            this.toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
            return this;
        }

        public Builder structuredOutput(Object structuredOutput) {
            this.structuredOutput = structuredOutput;
            return this;
        }

        public Builder stateUpdates(Map<StateKey<?>, Object> stateUpdates) {
            this.stateUpdates = stateUpdates == null ? Map.of() : Map.copyOf(stateUpdates);
            return this;
        }

        public Builder completed(boolean completed) {
            this.completed = completed;
            return this;
        }

        public Builder error(AgentError error) {
            this.error = error;
            return this;
        }

        public AgentResult build() {
            return new AgentResult(text, toolCalls, structuredOutput, stateUpdates, completed, error);
        }
    }
}
