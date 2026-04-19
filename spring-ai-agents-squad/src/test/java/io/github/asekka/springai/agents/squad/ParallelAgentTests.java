package io.github.asekka.springai.agents.squad;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.asekka.springai.agents.core.Agent;
import io.github.asekka.springai.agents.core.AgentContext;
import io.github.asekka.springai.agents.core.AgentResult;
import io.github.asekka.springai.agents.core.AgentUsage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


class ParallelAgentTests {

    @Test
    void runsBranchesAndConcatenatesTexts() {
        ParallelAgent parallel = ParallelAgent.builder()
                .branch("a", ctx -> AgentResult.ofText("ALPHA"))
                .branch("b", ctx -> AgentResult.ofText("BETA"))
                .build();

        AgentResult result = parallel.execute(AgentContext.of("go"));
        assertThat(result.text()).isEqualTo("ALPHA\n\nBETA");
        assertThat(result.completed()).isTrue();
    }

    @Test
    void branchesReallyRunInParallel() throws Exception {
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger peak = new AtomicInteger();

        Agent slow = ctx -> {
            int n = inFlight.incrementAndGet();
            peak.accumulateAndGet(n, Math::max);
            try { Thread.sleep(40); } catch (InterruptedException ignored) {}
            inFlight.decrementAndGet();
            return AgentResult.ofText("x");
        };

        ParallelAgent parallel = ParallelAgent.builder()
                .branch("a", slow)
                .branch("b", slow)
                .branch("c", slow)
                .build();

        parallel.execute(AgentContext.of("go"));
        assertThat(peak.get()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void failedBranchSurfacesAsErrorResultInCombiner() {
        ParallelAgent.Combiner capturing = (ctx, results) -> {
            assertThat(results.get("bad").hasError()).isTrue();
            return AgentResult.ofText("combined");
        };

        ParallelAgent parallel = ParallelAgent.builder()
                .branch("ok", ctx -> AgentResult.ofText("ok"))
                .branch("bad", ctx -> { throw new RuntimeException("boom"); })
                .combiner(capturing)
                .build();

        AgentResult r = parallel.execute(AgentContext.of("go"));
        assertThat(r.text()).isEqualTo("combined");
    }

    @Test
    void combinerSumsUsageAcrossBranches() {
        ParallelAgent parallel = ParallelAgent.builder()
                .branch("a", ctx -> AgentResult.builder()
                        .text("x").usage(AgentUsage.of(10, 5)).build())
                .branch("b", ctx -> AgentResult.builder()
                        .text("y").usage(AgentUsage.of(3, 2)).build())
                .build();

        AgentResult r = parallel.execute(AgentContext.of("go"));
        assertThat(r.usage()).isEqualTo(new AgentUsage(13, 7, 20));
    }

    @Test
    void timeoutReturnsFailedResultWhenBranchTooSlow() {
        ParallelAgent parallel = ParallelAgent.builder()
                .branch("slow", ctx -> {
                    try { Thread.sleep(5000); } catch (InterruptedException ignored) {}
                    return AgentResult.ofText("never");
                })
                .timeout(Duration.ofMillis(100))
                .build();

        AgentResult r = parallel.execute(AgentContext.of("go"));
        assertThat(r.hasError()).isTrue();
    }

    @Test
    void builderRejectsEmptyBranches() {
        assertThatThrownBy(() -> ParallelAgent.builder().build())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void builderRejectsDuplicateBranch() {
        assertThatThrownBy(() -> ParallelAgent.builder()
                .branch("a", ctx -> AgentResult.ofText("1"))
                .branch("a", ctx -> AgentResult.ofText("2"))
                .build())
                .isInstanceOf(IllegalStateException.class);
    }
}
