package io.github.asekka.springai.agents.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class StateBag {

    private static final StateBag EMPTY = new StateBag(Map.of());

    private final Map<StateKey<?>, Object> entries;

    private StateBag(Map<StateKey<?>, Object> entries) {
        this.entries = entries;
    }

    public static StateBag empty() {
        return EMPTY;
    }

    public <T> T get(StateKey<T> key) {
        Objects.requireNonNull(key, "key");
        Object value = entries.get(key);
        if (value == null) {
            return null;
        }
        return key.type().cast(value);
    }

    public <T> boolean contains(StateKey<T> key) {
        return entries.containsKey(Objects.requireNonNull(key, "key"));
    }

    public <T> StateBag put(StateKey<T> key, T value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        Map<StateKey<?>, Object> next = new LinkedHashMap<>(entries);
        next.put(key, value);
        return new StateBag(Collections.unmodifiableMap(next));
    }

    /**
     * Returns a new {@code StateBag} without the given key.
     * If the key is not present, returns {@code this} unchanged.
     */
    public <T> StateBag remove(StateKey<T> key) {
        Objects.requireNonNull(key, "key");
        if (!entries.containsKey(key)) {
            return this;
        }
        Map<StateKey<?>, Object> next = new LinkedHashMap<>(entries);
        next.remove(key);
        return new StateBag(Collections.unmodifiableMap(next));
    }

    public StateBag merge(Map<StateKey<?>, Object> updates) {
        if (updates == null || updates.isEmpty()) {
            return this;
        }
        Map<StateKey<?>, Object> next = new LinkedHashMap<>(entries);
        updates.forEach((key, value) -> {
            Objects.requireNonNull(key, "key in updates");
            if (value != null && !key.type().isInstance(value)) {
                throw new ClassCastException(
                    "Value for key '" + key.name() + "' is not of type " + key.type().getName());
            }
            next.put(key, value);
        });
        return new StateBag(Collections.unmodifiableMap(next));
    }

    public Set<StateKey<?>> keys() {
        return entries.keySet();
    }

    public int size() {
        return entries.size();
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof StateBag other && entries.equals(other.entries);
    }

    @Override
    public int hashCode() {
        return entries.hashCode();
    }

    @Override
    public String toString() {
        return "StateBag" + entries;
    }
}
