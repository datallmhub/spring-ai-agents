package io.github.asekka.springai.agents.checkpoint;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import io.github.asekka.springai.agents.core.AgentContext;
import io.github.asekka.springai.agents.core.StateKey;
import io.github.asekka.springai.agents.graph.Checkpoint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class RedisCheckpointStoreTests {

    public record Note(String text) {}

    private static final StateKey<Note> NOTE = StateKey.of("note", Note.class);

    @SuppressWarnings("unchecked")
    private RedisOperations<String, String> redis = mock(RedisOperations.class);
    @SuppressWarnings("unchecked")
    private ValueOperations<String, String> valueOps = mock(ValueOperations.class);
    private JacksonCheckpointCodec codec;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(valueOps);
        codec = new JacksonCheckpointCodec(new StateTypeRegistry().register(NOTE));
    }

    @Test
    void saveWritesCodecPayloadUnderPrefixedKey() {
        RedisCheckpointStore store = new RedisCheckpointStore(redis, codec);
        AgentContext ctx = AgentContext.empty()
                .withMessages(List.of(new UserMessage("hi")))
                .with(NOTE, new Note("v1"));
        Checkpoint cp = new Checkpoint("run-a", "plan", ctx, 1, null);

        store.save(cp);

        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> payload = ArgumentCaptor.forClass(String.class);
        verify(valueOps).set(key.capture(), payload.capture());
        assertThat(key.getValue()).isEqualTo("agent:checkpoint:run-a");
        assertThat(payload.getValue()).contains("\"runId\":\"run-a\"");
    }

    @Test
    void saveWithTtlUsesSetWithExpiration() {
        RedisCheckpointStore store = new RedisCheckpointStore(redis, codec,
                "agent:ckpt:", Duration.ofMinutes(30));
        Checkpoint cp = new Checkpoint("run-b", "plan", AgentContext.empty(), 0, null);

        store.save(cp);

        verify(valueOps).set(eq("agent:ckpt:run-b"), anyString(), eq(Duration.ofMinutes(30)));
        verifyNoMoreInteractions(valueOps);
    }

    @Test
    void loadReturnsCheckpointWhenKeyExists() {
        Checkpoint original = new Checkpoint("run-c", "plan",
                AgentContext.empty().with(NOTE, new Note("stored")), 2, null);
        when(valueOps.get("agent:checkpoint:run-c")).thenReturn(codec.encode(original));
        RedisCheckpointStore store = new RedisCheckpointStore(redis, codec);

        Optional<Checkpoint> loaded = store.load("run-c");

        assertThat(loaded).isPresent();
        assertThat(loaded.get().runId()).isEqualTo("run-c");
        assertThat(loaded.get().iterations()).isEqualTo(2);
        assertThat(loaded.get().context().get(NOTE)).isEqualTo(new Note("stored"));
    }

    @Test
    void loadReturnsEmptyWhenKeyMissing() {
        when(valueOps.get(any())).thenReturn(null);
        RedisCheckpointStore store = new RedisCheckpointStore(redis, codec);

        assertThat(store.load("nope")).isEmpty();
    }

    @Test
    void deleteIssuesRedisDeleteOnPrefixedKey() {
        RedisCheckpointStore store = new RedisCheckpointStore(redis, codec);

        store.delete("run-d");

        verify(redis).delete("agent:checkpoint:run-d");
    }

    @Test
    void constructorRejectsNonPositiveTtl() {
        assertThatThrownBy(() -> new RedisCheckpointStore(redis, codec, "k:", Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new RedisCheckpointStore(redis, codec, "k:", Duration.ofSeconds(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
