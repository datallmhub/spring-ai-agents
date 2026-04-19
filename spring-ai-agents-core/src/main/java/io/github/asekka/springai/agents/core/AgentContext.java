package io.github.asekka.springai.agents.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

public final class AgentContext {

    private static final AgentContext EMPTY = new AgentContext(List.of(), StateBag.empty());

    private final List<Message> messages;
    private final StateBag state;

    private AgentContext(List<Message> messages, StateBag state) {
        this.messages = messages;
        this.state = state;
    }

    public static AgentContext empty() {
        return EMPTY;
    }

    public static AgentContext of(Message... messages) {
        Objects.requireNonNull(messages, "messages");
        return new AgentContext(List.of(messages), StateBag.empty());
    }

    public static AgentContext of(String userMessage) {
        return of(new UserMessage(userMessage));
    }

    public List<Message> messages() {
        return messages;
    }

    public StateBag state() {
        return state;
    }

    public <T> T get(StateKey<T> key) {
        return state.get(key);
    }

    public <T> AgentContext with(StateKey<T> key, T value) {
        return new AgentContext(messages, state.put(key, value));
    }

    public AgentContext withMessage(Message message) {
        Objects.requireNonNull(message, "message");
        List<Message> next = new ArrayList<>(messages.size() + 1);
        next.addAll(messages);
        next.add(message);
        return new AgentContext(Collections.unmodifiableList(next), state);
    }

    public AgentContext withMessages(List<Message> additional) {
        if (additional == null || additional.isEmpty()) {
            return this;
        }
        List<Message> next = new ArrayList<>(messages.size() + additional.size());
        next.addAll(messages);
        next.addAll(additional);
        return new AgentContext(Collections.unmodifiableList(next), state);
    }

    public AgentContext withState(StateBag state) {
        Objects.requireNonNull(state, "state");
        return new AgentContext(messages, state);
    }

    public AgentContext applyResult(AgentResult result) {
        Objects.requireNonNull(result, "result");
        StateBag mergedState = result.stateUpdates().isEmpty()
                ? state
                : state.merge(result.stateUpdates());
        return new AgentContext(messages, mergedState);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof AgentContext other
                && messages.equals(other.messages)
                && state.equals(other.state);
    }

    @Override
    public int hashCode() {
        return Objects.hash(messages, state);
    }

    @Override
    public String toString() {
        return "AgentContext{messages=" + messages.size() + ", state=" + state + "}";
    }
}
