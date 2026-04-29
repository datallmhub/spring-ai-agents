package io.github.asekka.springai.agents.checkpoint;

import io.github.asekka.springai.agents.graph.Checkpoint;

public interface CheckpointCodec {

    String encode(Checkpoint checkpoint);

    Checkpoint decode(String payload);
}
