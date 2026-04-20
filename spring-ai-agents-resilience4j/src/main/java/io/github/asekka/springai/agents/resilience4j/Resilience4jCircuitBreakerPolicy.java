package io.github.asekka.springai.agents.resilience4j;

import java.util.Objects;
import java.util.function.Supplier;

import io.github.asekka.springai.agents.graph.CircuitBreakerPolicy;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;

/**
 * {@link CircuitBreakerPolicy} backed by Resilience4j. Two usage modes:
 *
 * <ul>
 *   <li><b>Registry mode</b> (default) — the registry resolves (or creates) a
 *   {@link CircuitBreaker} per node name. Use this when different nodes should
 *   trip independently, e.g. one breaker per upstream provider.</li>
 *   <li><b>Shared breaker mode</b> — every call goes through the same
 *   {@link CircuitBreaker}, regardless of node name. Use this when a single
 *   upstream backs multiple nodes and you want one aggregate failure count.</li>
 * </ul>
 *
 * <p>Only thrown exceptions count as failures: an {@code AgentResult} with an
 * {@code error} but no thrown exception will not trip the breaker.
 */
public final class Resilience4jCircuitBreakerPolicy implements CircuitBreakerPolicy {

    private final CircuitBreakerRegistry registry;
    private final CircuitBreaker sharedBreaker;

    public Resilience4jCircuitBreakerPolicy(CircuitBreakerRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.sharedBreaker = null;
    }

    public Resilience4jCircuitBreakerPolicy(CircuitBreaker sharedBreaker) {
        this.registry = null;
        this.sharedBreaker = Objects.requireNonNull(sharedBreaker, "sharedBreaker");
    }

    @Override
    public <T> T execute(String nodeName, Supplier<T> call) {
        Objects.requireNonNull(nodeName, "nodeName");
        Objects.requireNonNull(call, "call");
        CircuitBreaker cb = sharedBreaker != null ? sharedBreaker : registry.circuitBreaker(nodeName);
        return cb.executeSupplier(call);
    }
}
