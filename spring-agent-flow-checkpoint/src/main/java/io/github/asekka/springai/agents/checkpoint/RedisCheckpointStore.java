package io.github.asekka.springai.agents.checkpoint;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

import io.github.asekka.springai.agents.graph.Checkpoint;
import io.github.asekka.springai.agents.graph.CheckpointStore;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.jspecify.annotations.Nullable;

/**
 * Redis-backed {@link CheckpointStore}. Each run is stored under
 * {@code keyPrefix + runId} as a single JSON string produced by the
 * {@link CheckpointCodec}. Save is a single {@code SET} (optionally with
 * TTL); load and delete are single-op too, so every write is atomic.
 *
 * <p>Use the {@link #RedisCheckpointStore(RedisOperations, CheckpointCodec, String, Duration)}
 * constructor to set a TTL for automatic expiration; pass {@code null} (or use
 * the shorter constructor) to keep checkpoints indefinitely.
 */
public final class RedisCheckpointStore implements CheckpointStore {

    public static final String DEFAULT_KEY_PREFIX = "agent:checkpoint:";

    private final RedisOperations<String, String> redis;
    private final CheckpointCodec codec;
    private final String keyPrefix;
    @Nullable private final Duration ttl;

    public RedisCheckpointStore(RedisOperations<String, String> redis, CheckpointCodec codec) {
        this(redis, codec, DEFAULT_KEY_PREFIX, null);
    }

    public RedisCheckpointStore(RedisOperations<String, String> redis, CheckpointCodec codec,
                                String keyPrefix, @Nullable Duration ttl) {
        this.redis = Objects.requireNonNull(redis, "redis");
        this.codec = Objects.requireNonNull(codec, "codec");
        this.keyPrefix = Objects.requireNonNull(keyPrefix, "keyPrefix");
        if (ttl != null && (ttl.isZero() || ttl.isNegative())) {
            throw new IllegalArgumentException("ttl must be positive when set, got " + ttl);
        }
        this.ttl = ttl;
    }

    @Override
    public void save(Checkpoint checkpoint) {
        Objects.requireNonNull(checkpoint, "checkpoint");
        String key = key(checkpoint.runId());
        String payload = codec.encode(checkpoint);
        ValueOperations<String, String> ops = redis.opsForValue();
        if (ttl != null) {
            ops.set(key, payload, ttl);
        } else {
            ops.set(key, payload);
        }
    }

    @Override
    public Optional<Checkpoint> load(String runId) {
        Objects.requireNonNull(runId, "runId");
        String payload = redis.opsForValue().get(key(runId));
        return payload == null ? Optional.empty() : Optional.of(codec.decode(payload));
    }

    @Override
    public void delete(String runId) {
        Objects.requireNonNull(runId, "runId");
        redis.delete(key(runId));
    }

    private String key(String runId) {
        return keyPrefix + runId;
    }
}
