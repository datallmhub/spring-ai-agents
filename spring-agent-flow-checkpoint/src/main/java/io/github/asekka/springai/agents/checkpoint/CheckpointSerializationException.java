package io.github.asekka.springai.agents.checkpoint;

public class CheckpointSerializationException extends RuntimeException {

    public CheckpointSerializationException(String message) {
        super(message);
    }

    public CheckpointSerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}
