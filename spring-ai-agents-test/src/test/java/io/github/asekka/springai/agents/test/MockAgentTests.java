package io.github.asekka.springai.agents.test;

import io.github.asekka.springai.agents.core.AgentContext;
import io.github.asekka.springai.agents.core.AgentResult;
import io.github.asekka.springai.agents.graph.AgentGraph;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MockAgentTests {

    @Test
    void returnsScriptedTextsInOrder() {
        MockAgent agent = MockAgent.builder()
                .thenReturn("first")
                .thenReturn("second")
                .build();

        assertThat(agent.execute(AgentContext.empty()).text()).isEqualTo("first");
        assertThat(agent.execute(AgentContext.empty()).text()).isEqualTo("second");
        assertThat(agent.invocations()).isEqualTo(2);
    }

    @Test
    void fallbackAppliesAfterScriptExhausted() {
        MockAgent agent = MockAgent.builder()
                .thenReturn("once")
                .fallback(ctx -> AgentResult.ofText("fallback"))
                .build();

        assertThat(agent.execute(AgentContext.empty()).text()).isEqualTo("once");
        assertThat(agent.execute(AgentContext.empty()).text()).isEqualTo("fallback");
    }

    @Test
    void noScriptAndNoFallbackThrows() {
        MockAgent agent = MockAgent.builder().build();
        assertThatThrownBy(() -> agent.execute(AgentContext.empty()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void testGraphTraceRecordsVisits() {
        TestGraph.Trace trace = TestGraph.trace(AgentGraph.builder()
                .addNode("a", MockAgent.returning("A"))
                .addNode("b", MockAgent.returning("B"))
                .addEdge("a", "b"));

        trace.invoke(AgentContext.empty());
        assertThat(trace.visitedInOrder("a", "b")).isTrue();
        assertThat(trace.visited()).containsExactly("a", "b");
    }

    @Test
    void visitedInOrderFailsOnWrongSizeAndWrongElement() {
        TestGraph.Trace trace = TestGraph.trace(AgentGraph.builder()
                .addNode("a", MockAgent.returning("A"))
                .addNode("b", MockAgent.returning("B"))
                .addEdge("a", "b"));
        trace.invoke(AgentContext.empty());
        assertThat(trace.visitedInOrder("a")).isFalse();
        assertThat(trace.visitedInOrder("a", "z")).isFalse();
    }

    @Test
    void wrapExposesInvokeAndVisitedNodes() {
        AgentGraph graph = AgentGraph.builder()
                .addNode("only", MockAgent.returning("X"))
                .build();
        TestGraph wrapped = TestGraph.wrap(graph);
        AgentResult result = wrapped.invoke(AgentContext.empty());
        assertThat(result.text()).isEqualTo("X");
        assertThat(wrapped.visitedNodes()).isEmpty();
    }

    @Test
    void returningFromAgentResultFactoryWorks() {
        MockAgent agent = MockAgent.returning(AgentResult.ofText("hi"));
        assertThat(agent.execute(AgentContext.empty()).text()).isEqualTo("hi");
    }

    @Test
    void builderThenAnswerAndThenThrow() {
        MockAgent agent = MockAgent.builder()
                .thenAnswer(ctx -> AgentResult.ofText("dyn"))
                .thenThrow(new IllegalArgumentException("boom"))
                .build();
        assertThat(agent.execute(AgentContext.empty()).text()).isEqualTo("dyn");
        assertThatThrownBy(() -> agent.execute(AgentContext.empty()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void traceErrorOrNullReturnsResultError() {
        TestGraph.Trace trace = TestGraph.trace(AgentGraph.builder()
                .addNode("ok", MockAgent.returning("ok")));
        AgentResult result = trace.invoke(AgentContext.empty());
        assertThat(trace.errorOrNull(result)).isNull();
    }
}
