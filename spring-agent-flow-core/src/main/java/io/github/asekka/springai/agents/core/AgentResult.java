package io.github.asekka.springai.agents.core;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.lang.Nullable;

public record AgentResult(
        @Nullable String text,
        List<ToolCallRecord> toolCalls,
        @Nullable Object structuredOutput,
        Map<StateKey<?>, Object> stateUpdates,
        boolean completed,
        @Nullable AgentError error,
        @Nullable InterruptRequest interrupt,
        @Nullable AgentUsage usage) {

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

    public static AgentResult interrupted(String reason) {
        return interrupted(InterruptRequest.of(reason));
    }

    public static AgentResult interrupted(InterruptRequest request) {
        Objects.requireNonNull(request, "request");
        return new Builder().interrupt(request).completed(false).build();
    }

    public boolean hasError() {
        return error != null;
    }

    public boolean hasToolCalls() {
        return !toolCalls.isEmpty();
    }

    public boolean isInterrupted() {
        return interrupt != null;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String text;
        private List<ToolCallRecord> toolCalls = List.of();
        private Object structuredOutput;
        private Map<StateKey<?>, Object> stateUpdates = Map.of();
        private boolean completed = true;
        private AgentError error;
        private InterruptRequest interrupt;
        private AgentUsage usage;

        public Builder text(String text) {
            this.text = text;
            return this;
        }

        public Builder toolCalls(List<ToolCallRecord> toolCalls) {
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

        public Builder interrupt(InterruptRequest interrupt) {
            this.interrupt = interrupt;
            return this;
        }

        public Builder interrupt(String reason) {
            this.interrupt = InterruptRequest.of(reason);
            return this;
        }

        public Builder usage(AgentUsage usage) {
            this.usage = usage;
            return this;
        }

        public AgentResult build() {
            return new AgentResult(text, toolCalls, structuredOutput, stateUpdates,
                    completed, error, interrupt, usage);
        }
    }
}
