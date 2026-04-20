package io.github.asekka.springai.agents.checkpoint;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record CheckpointDto(
        int version,
        String runId,
        String nextNode,
        int iterations,
        String interruptReason,
        List<MessageDto> messages,
        List<StateEntryDto> state) {

    public static final int CURRENT_VERSION = 1;
}
