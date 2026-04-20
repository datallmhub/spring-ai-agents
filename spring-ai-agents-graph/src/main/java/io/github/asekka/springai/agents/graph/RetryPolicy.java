package io.github.asekka.springai.agents.graph;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Predicate;

public record RetryPolicy(
        int maxAttempts,
        Duration baseDelay,
        Duration maxDelay,
        double backoffMultiplier,
        double jitterFactor,
        Predicate<Throwable> retryOn) {

    public RetryPolicy {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
        Objects.requireNonNull(baseDelay, "baseDelay");
        Objects.requireNonNull(maxDelay, "maxDelay");
        Objects.requireNonNull(retryOn, "retryOn");
        if (baseDelay.isNegative() || maxDelay.isNegative()) {
            throw new IllegalArgumentException("delays must be non-negative");
        }
        if (maxDelay.compareTo(baseDelay) < 0) {
            throw new IllegalArgumentException("maxDelay must be >= baseDelay");
        }
        if (backoffMultiplier < 1.0) {
            throw new IllegalArgumentException("backoffMultiplier must be >= 1.0");
        }
        if (jitterFactor < 0.0 || jitterFactor > 1.0) {
            throw new IllegalArgumentException("jitterFactor must be in [0.0, 1.0]");
        }
    }

    public static RetryPolicy none() {
        return new RetryPolicy(1, Duration.ZERO, Duration.ZERO, 1.0, 0.0,
                RetryPredicates.never());
    }

    public static RetryPolicy once() {
        return new RetryPolicy(2, Duration.ZERO, Duration.ZERO, 1.0, 0.0,
                RetryPredicates.always());
    }

    public static RetryPolicy exponential(int maxAttempts, Duration baseDelay) {
        return new RetryPolicy(maxAttempts, baseDelay, baseDelay.multipliedBy(32),
                2.0, 0.2, RetryPredicates.transientIo());
    }

    public long computeDelayMs(int attemptNumber) {
        if (attemptNumber < 1) {
            throw new IllegalArgumentException("attemptNumber must be >= 1");
        }
        double raw = baseDelay.toMillis() * Math.pow(backoffMultiplier, attemptNumber - 1);
        long cap = Math.min((long) raw, maxDelay.toMillis());
        if (cap <= 0 || jitterFactor == 0.0) {
            return Math.max(0L, cap);
        }
        long jitterWindow = (long) (cap * jitterFactor);
        long floor = cap - jitterWindow;
        return floor + ThreadLocalRandom.current().nextLong(jitterWindow + 1);
    }
}
