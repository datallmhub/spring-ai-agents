package io.github.asekka.springai.agents.test;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import io.github.asekka.springai.agents.core.Agent;
import io.github.asekka.springai.agents.core.AgentContext;
import io.github.asekka.springai.agents.core.AgentResult;

public final class MockAgent implements Agent {

    private final Deque<Function<AgentContext, AgentResult>> responses;
    private final Function<AgentContext, AgentResult> fallback;
    private final AtomicInteger invocations = new AtomicInteger();

    private MockAgent(Builder b) {
        this.responses = new ArrayDeque<>(b.responses);
        this.fallback = b.fallback;
    }

    public static MockAgent returning(String text) {
        return builder().thenReturn(AgentResult.ofText(text)).build();
    }

    public static MockAgent returning(AgentResult result) {
        return builder().thenReturn(result).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public AgentResult execute(AgentContext context) {
        invocations.incrementAndGet();
        Function<AgentContext, AgentResult> next = responses.pollFirst();
        if (next != null) {
            return next.apply(context);
        }
        if (fallback != null) {
            return fallback.apply(context);
        }
        throw new IllegalStateException("MockAgent has no scripted response left");
    }

    public int invocations() {
        return invocations.get();
    }

    public static final class Builder {
        private final List<Function<AgentContext, AgentResult>> responses = new java.util.ArrayList<>();
        private Function<AgentContext, AgentResult> fallback;

        public Builder thenReturn(AgentResult result) {
            Objects.requireNonNull(result, "result");
            responses.add(ctx -> result);
            return this;
        }

        public Builder thenReturn(String text) {
            return thenReturn(AgentResult.ofText(text));
        }

        public Builder thenAnswer(Function<AgentContext, AgentResult> answer) {
            responses.add(Objects.requireNonNull(answer, "answer"));
            return this;
        }

        public Builder thenThrow(RuntimeException ex) {
            Objects.requireNonNull(ex, "ex");
            responses.add(ctx -> { throw ex; });
            return this;
        }

        public Builder fallback(Function<AgentContext, AgentResult> fallback) {
            this.fallback = fallback;
            return this;
        }

        public MockAgent build() {
            return new MockAgent(this);
        }
    }
}
