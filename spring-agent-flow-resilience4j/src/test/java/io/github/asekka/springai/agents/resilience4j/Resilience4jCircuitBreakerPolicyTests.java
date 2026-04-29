package io.github.asekka.springai.agents.resilience4j;

import java.time.Duration;

import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class Resilience4jCircuitBreakerPolicyTests {

    private static CircuitBreakerConfig fastTrip() {
        return CircuitBreakerConfig.custom()
                .failureRateThreshold(50.0f)
                .minimumNumberOfCalls(2)
                .slidingWindowSize(2)
                .waitDurationInOpenState(Duration.ofMinutes(1))
                .permittedNumberOfCallsInHalfOpenState(1)
                .build();
    }

    @Test
    void registryResolvesBreakerPerNodeName() {
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(fastTrip());
        Resilience4jCircuitBreakerPolicy policy = new Resilience4jCircuitBreakerPolicy(registry);

        // Trip breaker for node A by failing twice.
        for (int i = 0; i < 2; i++) {
            assertThatThrownBy(() -> policy.execute("node-a", () -> {
                throw new RuntimeException("boom");
            })).isInstanceOf(RuntimeException.class);
        }
        assertThat(registry.circuitBreaker("node-a").getState())
                .isEqualTo(CircuitBreaker.State.OPEN);

        // Node A is short-circuited now.
        assertThatThrownBy(() -> policy.execute("node-a", () -> "never"))
                .isInstanceOf(CallNotPermittedException.class);

        // Node B has its own breaker and still works.
        String out = policy.execute("node-b", () -> "ok");
        assertThat(out).isEqualTo("ok");
    }

    @Test
    void sharedBreakerModeAggregatesFailuresAcrossNodes() {
        CircuitBreaker shared = CircuitBreaker.of("shared", fastTrip());
        Resilience4jCircuitBreakerPolicy policy = new Resilience4jCircuitBreakerPolicy(shared);

        assertThatThrownBy(() -> policy.execute("node-x", () -> {
            throw new RuntimeException("x-fail");
        })).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> policy.execute("node-y", () -> {
            throw new RuntimeException("y-fail");
        })).isInstanceOf(RuntimeException.class);

        assertThat(shared.getState()).isEqualTo(CircuitBreaker.State.OPEN);

        // Both nodes short-circuit because they share the breaker.
        assertThatThrownBy(() -> policy.execute("node-x", () -> "nope"))
                .isInstanceOf(CallNotPermittedException.class);
        assertThatThrownBy(() -> policy.execute("node-y", () -> "nope"))
                .isInstanceOf(CallNotPermittedException.class);
    }

    @Test
    void happyPathReturnsCallResult() {
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();
        Resilience4jCircuitBreakerPolicy policy = new Resilience4jCircuitBreakerPolicy(registry);

        String out = policy.execute("node-ok", () -> "value");

        assertThat(out).isEqualTo("value");
        assertThat(registry.circuitBreaker("node-ok").getState())
                .isEqualTo(CircuitBreaker.State.CLOSED);
    }
}
