package io.github.asekka.springai.agents.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CoreValidationTests {

    @Test
    void stateKeyRejectsBlankName() {
        assertThatThrownBy(() -> StateKey.of("", String.class))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StateKey.of("   ", String.class))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void stateKeyRejectsNulls() {
        assertThatThrownBy(() -> StateKey.of(null, String.class))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> StateKey.of("x", null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void agentErrorRejectsInvalidInputs() {
        assertThatThrownBy(() -> new AgentError(null, new RuntimeException(), 0))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AgentError("n", null, 0))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new AgentError("n", new RuntimeException(), -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void agentErrorWithRetryCountPreservesIdentity() {
        AgentError err = AgentError.of("n", new RuntimeException("x"));
        AgentError bumped = err.withRetryCount(2);
        assertThat(bumped.retryCount()).isEqualTo(2);
        assertThat(bumped.nodeName()).isEqualTo("n");
    }

    @Test
    void agentEventStaticFactoriesProduceExpectedTypes() {
        AgentResult r = AgentResult.ofText("x");
        assertThat(AgentEvent.completed(r)).isInstanceOf(AgentEvent.Completed.class);
        assertThat(AgentEvent.token("chunk")).isInstanceOf(AgentEvent.Token.class);
        assertThat(AgentEvent.transition("a", "b")).isInstanceOf(AgentEvent.NodeTransition.class);
    }

    @Test
    void toolCallStartCopiesArgumentsMap() {
        java.util.Map<String, Object> args = new java.util.HashMap<>();
        args.put("k", "v");
        AgentEvent.ToolCallStart evt = new AgentEvent.ToolCallStart("tool", args);
        args.put("k2", "v2");
        assertThat(evt.toolName()).isEqualTo("tool");
        assertThat(evt.arguments()).hasSize(1).containsEntry("k", "v");
    }

    @Test
    void toolCallEndCarriesRecord() {
        ToolCallRecord rec = ToolCallRecord.success(1, "tool", java.util.Map.of("x", 1), "42", 12L);
        AgentEvent.ToolCallEnd evt = new AgentEvent.ToolCallEnd(rec);
        assertThat(evt.record()).isSameAs(rec);
        assertThat(evt.record().success()).isTrue();
        assertThat(evt.record().sequence()).isEqualTo(1);
    }

    @Test
    void stateBagContainsAndKeysBehaveConsistently() {
        StateKey<String> a = StateKey.of("a", String.class);
        StateKey<String> b = StateKey.of("b", String.class);
        StateBag bag = StateBag.empty().put(a, "1");
        assertThat(bag.contains(a)).isTrue();
        assertThat(bag.contains(b)).isFalse();
        assertThat(bag.keys()).containsExactly(a);
    }

    @Test
    void stateBagEqualsAndHashcodeReflectContent() {
        StateKey<String> k = StateKey.of("k", String.class);
        StateBag one = StateBag.empty().put(k, "v");
        StateBag two = StateBag.empty().put(k, "v");
        assertThat(one).isEqualTo(two).hasSameHashCodeAs(two);
        assertThat(one.toString()).contains("v");
    }

    @Test
    void agentContextWithMessagesAppendsAll() {
        AgentContext ctx = AgentContext.of("first")
                .withMessages(java.util.List.of(
                        new org.springframework.ai.chat.messages.UserMessage("second"),
                        new org.springframework.ai.chat.messages.UserMessage("third")));
        assertThat(ctx.messages()).hasSize(3);
    }

    @Test
    void agentContextWithMessagesEmptyIsNoop() {
        AgentContext ctx = AgentContext.of("x").withMessages(java.util.List.of());
        assertThat(ctx.messages()).hasSize(1);
    }

    @Test
    void agentContextApplyResultNoStateChangesKeepsBag() {
        AgentContext ctx = AgentContext.of("x");
        AgentResult r = AgentResult.ofText("y");
        assertThat(ctx.applyResult(r).state()).isSameAs(ctx.state());
    }

    @Test
    void agentResultBuilderHandlesAllFields() {
        AgentResult r = AgentResult.builder()
                .text("t")
                .structuredOutput(java.util.Map.of("ok", true))
                .completed(false)
                .build();
        assertThat(r.text()).isEqualTo("t");
        assertThat(r.structuredOutput()).isInstanceOf(java.util.Map.class);
        assertThat(r.completed()).isFalse();
    }
}
