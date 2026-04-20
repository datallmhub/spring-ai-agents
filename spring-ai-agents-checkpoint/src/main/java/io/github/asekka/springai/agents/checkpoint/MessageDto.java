package io.github.asekka.springai.agents.checkpoint;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MessageDto(
        int version,
        String role,
        String text,
        List<ToolCallDto> toolCalls,
        Map<String, Object> metadata) {

    public static final int CURRENT_VERSION = 1;
}
