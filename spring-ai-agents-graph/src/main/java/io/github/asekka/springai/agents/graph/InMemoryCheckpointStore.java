package io.github.asekka.springai.agents.graph;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InMemoryCheckpointStore implements CheckpointStore {

    private final ConcurrentMap<String, Checkpoint> store = new ConcurrentHashMap<>();

    @Override
    public void save(Checkpoint checkpoint) {
        Objects.requireNonNull(checkpoint, "checkpoint");
        store.put(checkpoint.runId(), checkpoint);
    }

    @Override
    public Optional<Checkpoint> load(String runId) {
        return Optional.ofNullable(store.get(Objects.requireNonNull(runId, "runId")));
    }

    @Override
    public void delete(String runId) {
        store.remove(Objects.requireNonNull(runId, "runId"));
    }

    public int size() {
        return store.size();
    }
}
