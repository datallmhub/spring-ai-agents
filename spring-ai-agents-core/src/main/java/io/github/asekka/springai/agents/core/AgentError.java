package io.github.asekka.springai.agents.core;

import java.util.Objects;

public record AgentError(String nodeName, Throwable cause, int retryCount) {

    public AgentError {
        Objects.requireNonNull(nodeName, "nodeName");
        Objects.requireNonNull(cause, "cause");
        if (retryCount < 0) {
            throw new IllegalArgumentException("retryCount must be >= 0");
        }
    }

    public static AgentError of(String nodeName, Throwable cause) {
        return new AgentError(nodeName, cause, 0);
    }

    public AgentError withRetryCount(int retryCount) {
        return new AgentError(nodeName, cause, retryCount);
    }
}
