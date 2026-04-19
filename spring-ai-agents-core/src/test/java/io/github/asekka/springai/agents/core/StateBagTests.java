package io.github.asekka.springai.agents.core;

import java.util.Map;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StateBagTests {

    private static final StateKey<String> NAME = StateKey.of("name", String.class);
    private static final StateKey<Integer> COUNT = StateKey.of("count", Integer.class);

    @Test
    void emptyBagIsEmpty() {
        assertThat(StateBag.empty().isEmpty()).isTrue();
        assertThat(StateBag.empty().size()).isZero();
    }

    @Test
    void putReturnsNewBag() {
        StateBag initial = StateBag.empty();
        StateBag updated = initial.put(NAME, "alice");

        assertThat(initial.get(NAME)).isNull();
        assertThat(updated.get(NAME)).isEqualTo("alice");
    }

    @Test
    void getReturnsTypedValue() {
        StateBag bag = StateBag.empty().put(COUNT, 42);
        Integer value = bag.get(COUNT);
        assertThat(value).isEqualTo(42);
    }

    @Test
    void containsReflectsPresence() {
        StateBag bag = StateBag.empty().put(NAME, "bob");
        assertThat(bag.contains(NAME)).isTrue();
        assertThat(bag.contains(COUNT)).isFalse();
    }

    @Test
    void mergeRejectsWrongType() {
        StateBag bag = StateBag.empty();
        Map<StateKey<?>, Object> bad = Map.of(NAME, 42);
        assertThatThrownBy(() -> bag.merge(bad))
                .isInstanceOf(ClassCastException.class);
    }

    @Test
    void mergeAppliesUpdates() {
        StateBag bag = StateBag.empty().put(NAME, "alice");
        StateBag merged = bag.merge(Map.of(COUNT, 3));
        assertThat(merged.get(NAME)).isEqualTo("alice");
        assertThat(merged.get(COUNT)).isEqualTo(3);
    }
}
