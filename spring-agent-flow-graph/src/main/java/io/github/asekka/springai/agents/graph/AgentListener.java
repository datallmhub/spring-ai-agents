package io.github.asekka.springai.agents.graph;

import io.github.asekka.springai.agents.core.AgentContext;
import io.github.asekka.springai.agents.core.AgentError;
import io.github.asekka.springai.agents.core.AgentResult;

public interface AgentListener {

    default void onNodeEnter(String graphName, String nodeName, AgentContext context) {}

    default void onNodeExit(String graphName, String nodeName, AgentResult result, long durationNanos) {}

    default void onNodeError(String graphName, String nodeName, AgentError error) {}

    /** Called each time the runtime moves from one node to another. */
    default void onTransition(String graphName, String from, String to) {}

    default void onGraphComplete(String graphName, AgentResult result) {}
}
