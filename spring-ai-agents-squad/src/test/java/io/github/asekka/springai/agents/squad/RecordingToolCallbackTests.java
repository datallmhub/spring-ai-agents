package io.github.asekka.springai.agents.squad;

import java.util.ArrayList;
import java.util.List;

import io.github.asekka.springai.agents.core.AgentEvent;
import io.github.asekka.springai.agents.core.ToolCallRecord;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RecordingToolCallbackTests {

    @Test
    void successCallIsRecordedWithParsedArgsAndResult() {
        ToolCallCollector collector = new ToolCallCollector();
        ToolCallback tool = stubTool("search", "\"result-for:foo\"");

        RecordingToolCallback wrapped = new RecordingToolCallback(tool, collector);
        String out = wrapped.call("{\"q\":\"foo\",\"limit\":10}");

        assertThat(out).isEqualTo("\"result-for:foo\"");
        List<ToolCallRecord> records = collector.snapshot();
        assertThat(records).hasSize(1);
        ToolCallRecord rec = records.get(0);
        assertThat(rec.name()).isEqualTo("search");
        assertThat(rec.arguments()).containsEntry("q", "foo").containsEntry("limit", 10);
        assertThat(rec.result()).isEqualTo("\"result-for:foo\"");
        assertThat(rec.error()).isNull();
        assertThat(rec.success()).isTrue();
        assertThat(rec.sequence()).isEqualTo(1);
        assertThat(rec.durationMs()).isGreaterThanOrEqualTo(0L);
    }

    @Test
    void failureCallIsRecordedWithErrorAndRethrows() {
        ToolCallCollector collector = new ToolCallCollector();
        ToolCallback tool = new ToolCallback() {
            @Override public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder().name("flaky").description("fails").inputSchema("{}").build();
            }
            @Override public String call(String toolInput) { throw new IllegalStateException("boom"); }
        };

        RecordingToolCallback wrapped = new RecordingToolCallback(tool, collector);
        assertThatThrownBy(() -> wrapped.call("{}"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("boom");

        assertThat(collector.snapshot()).hasSize(1);
        ToolCallRecord rec = collector.snapshot().get(0);
        assertThat(rec.success()).isFalse();
        assertThat(rec.error()).contains("IllegalStateException").contains("boom");
        assertThat(rec.result()).isNull();
        assertThat(rec.arguments()).isEmpty();
    }

    @Test
    void sinkReceivesStartAndEndEventsInOrder() {
        List<AgentEvent> events = new ArrayList<>();
        ToolCallCollector collector = new ToolCallCollector(events::add);
        ToolCallback tool = stubTool("calc", "\"42\"");

        new RecordingToolCallback(tool, collector).call("{\"x\":1}");

        assertThat(events).hasSize(2);
        assertThat(events.get(0)).isInstanceOfSatisfying(AgentEvent.ToolCallStart.class, s -> {
            assertThat(s.toolName()).isEqualTo("calc");
            assertThat(s.arguments()).containsEntry("x", 1);
        });
        assertThat(events.get(1)).isInstanceOfSatisfying(AgentEvent.ToolCallEnd.class, e -> {
            assertThat(e.record().name()).isEqualTo("calc");
            assertThat(e.record().result()).isEqualTo("\"42\"");
        });
    }

    @Test
    void nullToolInputYieldsEmptyArgsMap() {
        ToolCallCollector collector = new ToolCallCollector();
        RecordingToolCallback wrapped = new RecordingToolCallback(stubTool("ping", "\"pong\""), collector);

        wrapped.call(null);

        assertThat(collector.snapshot().get(0).arguments()).isEmpty();
    }

    @Test
    void malformedJsonArgumentsFallBackToRaw() {
        ToolCallCollector collector = new ToolCallCollector();
        RecordingToolCallback wrapped = new RecordingToolCallback(stubTool("ping", "\"pong\""), collector);

        wrapped.call("not-json-at-all");

        assertThat(collector.snapshot().get(0).arguments())
                .containsEntry("_raw", "not-json-at-all");
    }

    @Test
    void sequenceNumbersIncrementAcrossCalls() {
        ToolCallCollector collector = new ToolCallCollector();
        RecordingToolCallback wrapped = new RecordingToolCallback(stubTool("ping", "\"pong\""), collector);

        wrapped.call("{}");
        wrapped.call("{}");
        wrapped.call("{}");

        List<ToolCallRecord> recs = collector.snapshot();
        assertThat(recs).hasSize(3);
        assertThat(recs.get(0).sequence()).isEqualTo(1);
        assertThat(recs.get(1).sequence()).isEqualTo(2);
        assertThat(recs.get(2).sequence()).isEqualTo(3);
    }

    private static ToolCallback stubTool(String name, String returnValue) {
        return new ToolCallback() {
            @Override public ToolDefinition getToolDefinition() {
                return ToolDefinition.builder().name(name).description(name).inputSchema("{}").build();
            }
            @Override public String call(String toolInput) { return returnValue; }
        };
    }
}
