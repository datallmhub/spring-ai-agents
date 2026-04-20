package io.github.asekka.springai.agents.checkpoint;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ToolCallDto(String id, String name, String arguments) {}
