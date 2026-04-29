package io.github.asekka.springai.agents.samples;

import java.util.Map;

import io.github.asekka.springai.agents.core.Agent;
import io.github.asekka.springai.agents.core.AgentContext;
import io.github.asekka.springai.agents.core.AgentResult;
import io.github.asekka.springai.agents.core.StateKey;
import io.github.asekka.springai.agents.squad.CoordinatorAgent;
import io.github.asekka.springai.agents.squad.RoutingStrategy;

/**
 * 02 — Multi-agent coordination with the Squad API.
 *
 * <p>A coordinator routes the user request to the right executor
 * using a simple keyword-based strategy. No LLM required.
 *
 * <p>Expected output:
 * <pre>
 * === Multi-Agent Coordination ===
 *
 * Request: "Research the latest advances in quantum computing"
 * [router]   Routing to: research
 * [research] Searching for: quantum computing
 *
 * Result: Found 3 papers on quantum error correction (2026).
 *         Key finding: logical qubit fidelity reached 99.8%.
 *
 * ---
 *
 * Request: "Write a blog post about the findings"
 * [router]   Routing to: writing
 * [writing]  Generating content...
 *
 * Result: # Quantum Computing in 2026
 *         Logical qubit fidelity has reached 99.8%, marking a milestone...
 * </pre>
 */
public final class MultiAgentCoordination {

    public static void main(String[] args) {
        System.out.println("=== Multi-Agent Coordination ===");

        // Typed state keys
        StateKey<String> TOPIC = StateKey.of("topic", String.class);

        // Specialized agents (no LLM, just logic)
        Agent researcher = ctx -> {
            System.out.println("[research] Searching for: quantum computing");
            return AgentResult.builder()
                    .text("Found 3 papers on quantum error correction (2026).\n"
                        + "         Key finding: logical qubit fidelity reached 99.8%.")
                    .stateUpdates(Map.of(TOPIC, "quantum computing"))
                    .completed(true)
                    .build();
        };

        Agent writer = ctx -> {
            System.out.println("[writing]  Generating content...");
            String topic = ctx.get(TOPIC);
            return AgentResult.ofText(
                    "# Quantum Computing in 2026\n"
                  + "         Logical qubit fidelity has reached 99.8%, marking a milestone...");
        };

        Agent analyzer = ctx -> {
            System.out.println("[analysis] Analyzing data...");
            return AgentResult.ofText("Analysis complete: trend is strongly positive.");
        };

        // Keyword-based routing (in production, use RoutingStrategy.llmDriven(chatClient))
        RoutingStrategy keywordRouter = (ctx, executors) -> {
            String text = ctx.messages().get(0).getText().toLowerCase();
            System.out.println("[router]   Routing to: "
                    + (text.contains("research") ? "research"
                    :  text.contains("write") ? "writing" : "analysis"));
            if (text.contains("research")) return "research";
            if (text.contains("write")) return "writing";
            return "analysis";
        };

        CoordinatorAgent coordinator = CoordinatorAgent.builder()
                .name("project-supervisor")
                .executors(Map.of(
                        "research", researcher,
                        "analysis", analyzer,
                        "writing",  writer
                ))
                .routingStrategy(keywordRouter)
                .build();

        // Request 1 — routes to research
        System.out.println();
        System.out.println("Request: \"Research the latest advances in quantum computing\"");
        AgentResult r1 = coordinator.execute(
                AgentContext.of("Research the latest advances in quantum computing"));
        System.out.println();
        System.out.println("Result: " + r1.text());

        // Request 2 — routes to writing
        System.out.println();
        System.out.println("---");
        System.out.println();
        System.out.println("Request: \"Write a blog post about the findings\"");
        AgentResult r2 = coordinator.execute(
                AgentContext.of("Write a blog post about the findings"));
        System.out.println();
        System.out.println("Result: " + r2.text());
    }
}
