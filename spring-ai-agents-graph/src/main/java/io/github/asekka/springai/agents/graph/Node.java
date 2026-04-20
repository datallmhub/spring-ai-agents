package io.github.asekka.springai.agents.graph;

import io.github.asekka.springai.agents.core.Agent;
import io.github.asekka.springai.agents.core.AgentContext;
import io.github.asekka.springai.agents.core.AgentEvent;
import io.github.asekka.springai.agents.core.AgentResult;
import org.springframework.lang.Nullable;
import reactor.core.publisher.Flux;

public interface Node {

    String name();

    AgentResult execute(AgentContext context);

    default Flux<AgentEvent> executeStream(AgentContext context) {
        return Flux.just(AgentEvent.completed(execute(context)));
    }

    @Nullable
    default RetryPolicy retryPolicy() {
        return null;
    }

    static Node of(String name, Agent agent) {
        return new AgentNode(name, agent, null);
    }

    static Node of(String name, Agent agent, RetryPolicy retryPolicy) {
        return new AgentNode(name, agent, retryPolicy);
    }

    record AgentNode(String name, Agent agent, @Nullable RetryPolicy retryPolicy) implements Node {
        @Override
        public AgentResult execute(AgentContext context) {
            return agent.execute(context);
        }

        @Override
        public Flux<AgentEvent> executeStream(AgentContext context) {
            return agent.executeStream(context);
        }
    }
}
