package io.github.asekka.springai.agents.test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import io.github.asekka.springai.agents.core.AgentContext;
import io.github.asekka.springai.agents.core.AgentError;
import io.github.asekka.springai.agents.core.AgentResult;
import io.github.asekka.springai.agents.graph.AgentGraph;
import io.github.asekka.springai.agents.graph.AgentListener;

public final class TestGraph {

    private final AgentGraph graph;
    private final List<String> visited = Collections.synchronizedList(new ArrayList<>());

    private TestGraph(AgentGraph graph) {
        this.graph = graph;
    }

    public static TestGraph wrap(AgentGraph graph) {
        TestGraph tg = new TestGraph(graph);
        return tg;
    }

    public static Trace trace(AgentGraph.Builder builder) {
        List<String> visited = Collections.synchronizedList(new ArrayList<>());
        AgentListener listener = new AgentListener() {
            @Override
            public void onNodeEnter(String graphName, String nodeName, AgentContext context) {
                visited.add(nodeName);
            }
        };
        AgentGraph graph = builder.listener(listener).build();
        return new Trace(graph, visited);
    }

    public List<String> visitedNodes() {
        return List.copyOf(visited);
    }

    public AgentResult invoke(AgentContext initial) {
        return graph.invoke(initial);
    }

    public static final class Trace {
        private final AgentGraph graph;
        private final List<String> visited;

        Trace(AgentGraph graph, List<String> visited) {
            this.graph = graph;
            this.visited = visited;
        }

        public AgentResult invoke(AgentContext initial) {
            return graph.invoke(initial);
        }

        public List<String> visited() {
            return List.copyOf(visited);
        }

        public boolean visitedInOrder(String... expected) {
            List<String> actual = visited();
            if (actual.size() != expected.length) {
                return false;
            }
            for (int i = 0; i < expected.length; i++) {
                if (!expected[i].equals(actual.get(i))) {
                    return false;
                }
            }
            return true;
        }

        public AgentError errorOrNull(AgentResult result) {
            return result.error();
        }
    }
}
