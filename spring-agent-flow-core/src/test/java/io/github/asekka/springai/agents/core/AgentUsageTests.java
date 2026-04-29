package io.github.asekka.springai.agents.core;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentUsageTests {

    @Test
    void zeroReturnsEmptyUsage() {
        AgentUsage u = AgentUsage.zero();
        assertThat(u.promptTokens()).isZero();
        assertThat(u.completionTokens()).isZero();
        assertThat(u.totalTokens()).isZero();
    }

    @Test
    void ofComputesTotal() {
        AgentUsage u = AgentUsage.of(7, 3);
        assertThat(u.totalTokens()).isEqualTo(10L);
    }

    @Test
    void addCombinesTokens() {
        AgentUsage a = AgentUsage.of(2, 3);
        AgentUsage b = AgentUsage.of(5, 1);
        AgentUsage sum = a.add(b);
        assertThat(sum).isEqualTo(new AgentUsage(7, 4, 11));
    }

    @Test
    void addTreatsNullAsIdentity() {
        AgentUsage a = AgentUsage.of(1, 2);
        assertThat(a.add(null)).isEqualTo(a);
    }

    @Test
    void rejectsNegativeTokens() {
        assertThatThrownBy(() -> new AgentUsage(-1, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AgentUsage(0, -1, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new AgentUsage(0, 0, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
