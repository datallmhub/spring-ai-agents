package io.github.asekka.springai.agents.core;

import java.util.Map;

public sealed interface AgentEvent {

    record Token(String chunk) implements AgentEvent {}

    record ToolCallStart(String toolName, Map<String, Object> args) implements AgentEvent {
        public ToolCallStart {
            args = args == null ? Map.of() : Map.copyOf(args);
        }
    }

    record ToolCallEnd(String toolName, Object result) implements AgentEvent {}

    record NodeTransition(String from, String to) implements AgentEvent {}

    record Completed(AgentResult result) implements AgentEvent {}

    static Completed completed(AgentResult result) {
        return new Completed(result);
    }

    static Token token(String chunk) {
        return new Token(chunk);
    }

    static NodeTransition transition(String from, String to) {
        return new NodeTransition(from, to);
    }
}
