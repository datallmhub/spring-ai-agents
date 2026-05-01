package io.github.asekka.springai.agents.squad;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import io.github.asekka.springai.agents.core.AgentEvent;
import io.github.asekka.springai.agents.core.ToolCallRecord;
import org.jspecify.annotations.Nullable;

final class ToolCallCollector {

    private final List<ToolCallRecord> records = new ArrayList<>();
    private final Object lock = new Object();
    private final AtomicInteger sequenceCounter = new AtomicInteger();
    @Nullable
    private final Consumer<AgentEvent> sink;

    ToolCallCollector() {
        this(null);
    }

    ToolCallCollector(@Nullable Consumer<AgentEvent> sink) {
        this.sink = sink;
    }

    int nextSequence() {
        return sequenceCounter.incrementAndGet();
    }

    void onStart(String name, Map<String, Object> arguments) {
        if (sink != null) {
            sink.accept(new AgentEvent.ToolCallStart(name, arguments));
        }
    }

    void onEnd(ToolCallRecord record) {
        synchronized (lock) {
            records.add(record);
        }
        if (sink != null) {
            sink.accept(new AgentEvent.ToolCallEnd(record));
        }
    }

    List<ToolCallRecord> snapshot() {
        synchronized (lock) {
            return List.copyOf(records);
        }
    }
}
