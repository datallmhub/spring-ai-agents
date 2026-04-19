package io.github.asekka.springai.agents.squad;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import io.github.asekka.springai.agents.core.Agent;
import io.github.asekka.springai.agents.core.AgentContext;
import io.github.asekka.springai.agents.core.AgentError;
import io.github.asekka.springai.agents.core.AgentResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CoordinatorAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(CoordinatorAgent.class);

    private final String name;
    private final Map<String, Agent> executors;
    private final RoutingStrategy routingStrategy;

    private CoordinatorAgent(Builder b) {
        this.name = b.name;
        if (b.executors.isEmpty()) {
            throw new IllegalStateException("CoordinatorAgent requires at least one executor");
        }
        this.executors = Collections.unmodifiableMap(new LinkedHashMap<>(b.executors));
        this.routingStrategy = Objects.requireNonNull(b.routingStrategy, "routingStrategy");
    }

    public static Builder builder() {
        return new Builder();
    }

    public String name() {
        return name;
    }

    @Override
    public AgentResult execute(AgentContext context) {
        String chosen;
        try {
            chosen = routingStrategy.selectExecutor(context, executors.keySet());
        }
        catch (Throwable t) {
            return AgentResult.failed(AgentError.of(name, t));
        }

        Agent executor = executors.get(chosen);
        if (executor == null) {
            return AgentResult.failed(AgentError.of(name,
                    new IllegalStateException("Router returned unknown executor: " + chosen)));
        }

        log.debug("Coordinator '{}' routing to executor '{}'", name, chosen);
        try {
            return executor.execute(context);
        }
        catch (Throwable t) {
            return AgentResult.failed(AgentError.of(chosen, t));
        }
    }

    public static final class Builder {
        private String name = "coordinator";
        private final Map<String, Agent> executors = new LinkedHashMap<>();
        private RoutingStrategy routingStrategy = RoutingStrategy.first();

        public Builder name(String name) {
            this.name = Objects.requireNonNull(name, "name");
            return this;
        }

        public Builder executor(String name, Agent agent) {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(agent, "agent");
            executors.put(name, agent);
            return this;
        }

        public Builder executors(Map<String, ? extends Agent> executors) {
            Objects.requireNonNull(executors, "executors");
            executors.forEach(this::executor);
            return this;
        }

        public Builder routingStrategy(RoutingStrategy strategy) {
            this.routingStrategy = Objects.requireNonNull(strategy, "strategy");
            return this;
        }

        public CoordinatorAgent build() {
            return new CoordinatorAgent(this);
        }
    }
}
