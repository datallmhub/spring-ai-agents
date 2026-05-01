package io.github.asekka.springai.agents.core;

import java.util.Objects;

import org.jspecify.annotations.Nullable;

public record InterruptRequest(String reason, @Nullable Object payload) {

    public InterruptRequest {
        Objects.requireNonNull(reason, "reason");
        if (reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
    }

    public static InterruptRequest of(String reason) {
        return new InterruptRequest(reason, null);
    }
}
