package io.github.asekka.springai.agents.core;

import java.util.Objects;

public record StateKey<T>(String name, Class<T> type) {

    public StateKey {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        if (name.isBlank()) {
            throw new IllegalArgumentException("StateKey name must not be blank");
        }
    }

    public static <T> StateKey<T> of(String name, Class<T> type) {
        return new StateKey<>(name, type);
    }
}
