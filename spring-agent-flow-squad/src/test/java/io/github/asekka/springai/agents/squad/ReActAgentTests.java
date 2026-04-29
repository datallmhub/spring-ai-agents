package io.github.asekka.springai.agents.squad;

import java.util.concurrent.atomic.AtomicInteger;

import io.github.asekka.springai.agents.core.Agent;
import io.github.asekka.springai.agents.core.AgentContext;
import io.github.asekka.springai.agents.core.AgentError;
import io.github.asekka.springai.agents.core.AgentResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReActAgentTests {

    @Test
    void stopsOnFirstCompletedResult() {
        Agent inner = ctx -> AgentResult.ofText("done");
        ReActAgent react = ReActAgent.builder().inner(inner).build();

        AgentResult result = react.execute(AgentContext.of("go"));

        assertThat(result.text()).isEqualTo("done");
        assertThat(result.completed()).isTrue();
    }

    @Test
    void loopsUntilCompletedFlagFlips() {
        AtomicInteger calls = new AtomicInteger();
        Agent inner = ctx -> {
            int n = calls.incrementAndGet();
            if (n < 3) {
                return AgentResult.builder().text("thinking-" + n).completed(false).build();
            }
            return AgentResult.ofText("final");
        };

        ReActAgent react = ReActAgent.builder().inner(inner).maxSteps(5).build();
        AgentResult result = react.execute(AgentContext.of("start"));

        assertThat(calls.get()).isEqualTo(3);
        assertThat(result.text()).isEqualTo("final");
    }

    @Test
    void appendsAssistantMessagesBetweenSteps() {
        AtomicInteger step = new AtomicInteger();
        Agent inner = ctx -> {
            int n = step.incrementAndGet();
            if (n == 1) {
                return AgentResult.builder().text("first").completed(false).build();
            }
            // second call: context should contain the assistant message from step 1
            assertThat(ctx.messages()).hasSize(2);
            assertThat(ctx.messages().get(1).getText()).isEqualTo("first");
            return AgentResult.ofText("second");
        };

        ReActAgent react = ReActAgent.builder().inner(inner).maxSteps(3).build();
        AgentResult result = react.execute(AgentContext.of("hi"));

        assertThat(result.text()).isEqualTo("second");
    }

    @Test
    void returnsFailureWhenMaxStepsExceeded() {
        Agent inner = ctx -> AgentResult.builder().text("still-going").completed(false).build();
        ReActAgent react = ReActAgent.builder().inner(inner).maxSteps(2).build();

        AgentResult result = react.execute(AgentContext.of("start"));

        assertThat(result.hasError()).isTrue();
        assertThat(result.error().cause()).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void wrapsThrownExceptionAsAgentError() {
        Agent inner = ctx -> { throw new RuntimeException("boom"); };
        ReActAgent react = ReActAgent.builder().inner(inner).build();

        AgentResult result = react.execute(AgentContext.of("x"));

        assertThat(result.hasError()).isTrue();
    }

    @Test
    void propagatesInnerErrorImmediately() {
        Agent inner = ctx -> AgentResult.failed(AgentError.of("n", new IllegalArgumentException("x")));
        ReActAgent react = ReActAgent.builder().inner(inner).build();

        AgentResult result = react.execute(AgentContext.of("x"));

        assertThat(result.hasError()).isTrue();
        assertThat(result.error().cause()).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void customStopPredicateOverridesCompletedFlag() {
        AtomicInteger n = new AtomicInteger();
        Agent inner = ctx -> AgentResult.ofText("iter-" + n.incrementAndGet());

        ReActAgent react = ReActAgent.builder()
                .inner(inner)
                .maxSteps(5)
                .stopWhen((ctx, res) -> "iter-3".equals(res.text()))
                .build();

        AgentResult result = react.execute(AgentContext.of("go"));
        assertThat(result.text()).isEqualTo("iter-3");
        assertThat(n.get()).isEqualTo(3);
    }

    @Test
    void builderRejectsNonPositiveMaxStepsAndNullInner() {
        assertThatThrownBy(() -> ReActAgent.builder().maxSteps(0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> ReActAgent.builder().build())
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nameDefaultsToReactAndIsOverridable() {
        Agent inner = ctx -> AgentResult.ofText("x");
        assertThat(ReActAgent.builder().inner(inner).build().name()).isEqualTo("react");
        assertThat(ReActAgent.builder().name("loop").inner(inner).build().name()).isEqualTo("loop");
    }
}
