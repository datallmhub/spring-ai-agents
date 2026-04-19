package io.github.asekka.springai.agents.core;

public record AgentUsage(long promptTokens, long completionTokens, long totalTokens) {

    public AgentUsage {
        if (promptTokens < 0 || completionTokens < 0 || totalTokens < 0) {
            throw new IllegalArgumentException("token counts must be >= 0");
        }
    }

    public static AgentUsage zero() {
        return new AgentUsage(0L, 0L, 0L);
    }

    public static AgentUsage of(long promptTokens, long completionTokens) {
        return new AgentUsage(promptTokens, completionTokens, promptTokens + completionTokens);
    }

    public AgentUsage add(AgentUsage other) {
        if (other == null) {
            return this;
        }
        return new AgentUsage(
                this.promptTokens + other.promptTokens,
                this.completionTokens + other.completionTokens,
                this.totalTokens + other.totalTokens);
    }
}
