package io.github.asekka.springai.agents.graph;

import java.util.function.BiPredicate;
import java.util.function.Predicate;

import io.github.asekka.springai.agents.core.AgentContext;
import io.github.asekka.springai.agents.core.AgentResult;

public sealed interface Edge {

    String from();

    String to();

    record Direct(String from, String to) implements Edge {}

    record Conditional(String from, Predicate<AgentContext> when, String to) implements Edge {
        public boolean matches(AgentContext context) {
            return when.test(context);
        }
    }

    record OnResult(String from, BiPredicate<AgentContext, AgentResult> when, String to)
            implements Edge {
        public boolean matches(AgentContext context, AgentResult result) {
            return when.test(context, result);
        }
    }

    static Direct direct(String from, String to) {
        return new Direct(from, to);
    }

    static Conditional conditional(String from, Predicate<AgentContext> when, String to) {
        return new Conditional(from, when, to);
    }

    static OnResult onResult(String from, BiPredicate<AgentContext, AgentResult> when, String to) {
        return new OnResult(from, when, to);
    }
}
