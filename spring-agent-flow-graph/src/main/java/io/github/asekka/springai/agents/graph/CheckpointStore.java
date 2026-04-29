package io.github.asekka.springai.agents.graph;

import java.util.Optional;

public interface CheckpointStore {

    void save(Checkpoint checkpoint);

    Optional<Checkpoint> load(String runId);

    void delete(String runId);
}
