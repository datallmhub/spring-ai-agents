package io.github.asekka.springai.agents.core;

import java.util.Map;

public sealed interface AgentEvent {

    record Token(String chunk) implements AgentEvent {}

    record ToolCallStart(String toolName, Map<String, Object> arguments) implements AgentEvent {
        public ToolCallStart {
            arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
        }
    }

    record ToolCallEnd(ToolCallRecord record) implements AgentEvent {}

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
