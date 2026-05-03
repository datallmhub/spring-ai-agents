package io.github.asekka.springai.agents.core;

import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

public record ToolCallRecord(
        int sequence,
        String name,
        Map<String, Object> arguments,
        @Nullable String result,
        @Nullable String error,
        long durationMs) {

    public ToolCallRecord {
        Objects.requireNonNull(name, "name");
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
    }

    public boolean success() {
        return error == null;
    }

    public static ToolCallRecord success(int sequence, String name, Map<String, Object> arguments,
            String result, long durationMs) {
        return new ToolCallRecord(sequence, name, arguments, result, null, durationMs);
    }

    public static ToolCallRecord failure(int sequence, String name, Map<String, Object> arguments,
            String error, long durationMs) {
        return new ToolCallRecord(sequence, name, arguments, null, error, durationMs);
    }
}
