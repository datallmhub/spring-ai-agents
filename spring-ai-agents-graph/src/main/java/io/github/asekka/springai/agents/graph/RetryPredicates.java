package io.github.asekka.springai.agents.graph;

import java.io.IOException;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

public final class RetryPredicates {

    private RetryPredicates() {}

    public static Predicate<Throwable> never() {
        return t -> false;
    }

    public static Predicate<Throwable> always() {
        return t -> true;
    }

    public static Predicate<Throwable> transientIo() {
        return t -> t instanceof IOException || t instanceof TimeoutException;
    }
}
