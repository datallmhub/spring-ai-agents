package io.github.asekka.springai.agents.squad;

import java.util.Objects;
import java.util.function.BiPredicate;

import io.github.asekka.springai.agents.core.Agent;
import io.github.asekka.springai.agents.core.AgentContext;
import io.github.asekka.springai.agents.core.AgentError;
import io.github.asekka.springai.agents.core.AgentResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ReActAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(ReActAgent.class);

    private final String name;
    private final Agent inner;
    private final int maxSteps;
    private final BiPredicate<AgentContext, AgentResult> stopWhen;

    private ReActAgent(Builder b) {
        this.name = b.name;
        this.inner = Objects.requireNonNull(b.inner, "inner");
        this.maxSteps = b.maxSteps;
        this.stopWhen = b.stopWhen != null ? b.stopWhen : (ctx, res) -> res.completed();
    }

    public static Builder builder() {
        return new Builder();
    }

    public String name() {
        return name;
    }

    @Override
    public AgentResult execute(AgentContext context) {
        AgentContext current = context;
        AgentResult last = null;
        for (int step = 1; step <= maxSteps; step++) {
            AgentResult result;
            try {
                result = inner.execute(current);
            }
            catch (Throwable t) {
                return AgentResult.failed(AgentError.of(name, t));
            }

            if (result.hasError()) {
                return result;
            }

            last = result;
            if (stopWhen.test(current, result)) {
                log.debug("ReAct '{}' stopped after {} step(s)", name, step);
                return result;
            }

            current = current.applyResult(result);
        }

        return AgentResult.failed(AgentError.of(name,
                new IllegalStateException("ReAct loop exceeded maxSteps=" + maxSteps
                        + " without stop condition"
                        + (last != null ? " (last text=" + last.text() + ")" : ""))));
    }

    public static final class Builder {
        private String name = "react";
        private Agent inner;
        private int maxSteps = 10;
        private BiPredicate<AgentContext, AgentResult> stopWhen;

        public Builder name(String name) {
            this.name = Objects.requireNonNull(name, "name");
            return this;
        }

        public Builder inner(Agent inner) {
            this.inner = inner;
            return this;
        }

        public Builder maxSteps(int maxSteps) {
            if (maxSteps <= 0) {
                throw new IllegalArgumentException("maxSteps must be > 0");
            }
            this.maxSteps = maxSteps;
            return this;
        }

        public Builder stopWhen(BiPredicate<AgentContext, AgentResult> predicate) {
            this.stopWhen = Objects.requireNonNull(predicate, "predicate");
            return this;
        }

        public ReActAgent build() {
            return new ReActAgent(this);
        }
    }
}
