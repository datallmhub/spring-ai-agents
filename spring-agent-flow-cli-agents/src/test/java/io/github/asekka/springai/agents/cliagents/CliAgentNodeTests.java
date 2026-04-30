package io.github.asekka.springai.agents.cliagents;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.github.asekka.springai.agents.core.AgentContext;
import io.github.asekka.springai.agents.core.AgentError;
import io.github.asekka.springai.agents.core.AgentResult;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springaicommunity.agents.model.AgentGeneration;
import org.springaicommunity.agents.model.AgentGenerationMetadata;
import org.springaicommunity.agents.model.AgentModel;
import org.springaicommunity.agents.model.AgentResponse;
import org.springaicommunity.agents.model.AgentTaskRequest;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CliAgentNodeTests {

    private static final Path WORKDIR = Paths.get("/tmp/cli-agent-workspace");

    private static AgentResponse okResponse(String text) {
        AgentGenerationMetadata md = new AgentGenerationMetadata("SUCCESS", Map.of());
        return new AgentResponse(List.of(new AgentGeneration(text, md)));
    }

    private static AgentResponse failedResponse(String text) {
        AgentGenerationMetadata md = new AgentGenerationMetadata("ERROR", Map.of());
        return new AgentResponse(List.of(new AgentGeneration(text, md)));
    }

    @Test
    void successfulAgentResponseMapsToCompletedResult() {
        AgentModel model = mock(AgentModel.class);
        when(model.call(any())).thenReturn(okResponse("created hello.txt"));
        CliAgentNode node = CliAgentNode.builder()
                .name("claude")
                .model(model)
                .workingDirectory(WORKDIR)
                .build();

        AgentResult result = node.execute(AgentContext.of("create hello.txt"));

        assertThat(result.completed()).isTrue();
        assertThat(result.hasError()).isFalse();
        assertThat(result.text()).isEqualTo("created hello.txt");
        assertThat(result.structuredOutput()).isInstanceOf(AgentResponse.class);
    }

    @Test
    void unsuccessfulAgentResponseMapsToFailedResult() {
        AgentModel model = mock(AgentModel.class);
        when(model.call(any())).thenReturn(failedResponse("permission denied"));
        CliAgentNode node = CliAgentNode.builder()
                .model(model).workingDirectory(WORKDIR).build();

        AgentResult result = node.execute(AgentContext.of("write /etc/hosts"));

        assertThat(result.hasError()).isTrue();
        assertThat(result.error().cause()).hasMessageContaining("permission denied");
    }

    @Test
    void lastUserMessageIsExtractedAsGoal() {
        AgentModel model = mock(AgentModel.class);
        when(model.call(any())).thenReturn(okResponse("ok"));
        CliAgentNode node = CliAgentNode.builder()
                .model(model).workingDirectory(WORKDIR).build();

        AgentContext ctx = AgentContext.empty()
                .withMessages(List.of(
                        new UserMessage("first user message"),
                        new AssistantMessage("assistant reply"),
                        new UserMessage("the actual goal")));
        node.execute(ctx);

        ArgumentCaptor<AgentTaskRequest> captor = ArgumentCaptor.forClass(AgentTaskRequest.class);
        verify(model).call(captor.capture());
        assertThat(captor.getValue().goal()).isEqualTo("the actual goal");
        assertThat(captor.getValue().workingDirectory()).isEqualTo(WORKDIR);
    }

    @Test
    void customGoalExtractorIsHonoured() {
        AgentModel model = mock(AgentModel.class);
        when(model.call(any())).thenReturn(okResponse("done"));
        CliAgentNode node = CliAgentNode.builder()
                .model(model).workingDirectory(WORKDIR)
                .goalExtractor(ctx -> "fixed-goal-from-extractor")
                .build();

        node.execute(AgentContext.of("ignored"));

        ArgumentCaptor<AgentTaskRequest> captor = ArgumentCaptor.forClass(AgentTaskRequest.class);
        verify(model).call(captor.capture());
        assertThat(captor.getValue().goal()).isEqualTo("fixed-goal-from-extractor");
    }

    @Test
    void fixedGoalConvenienceWorks() {
        AgentModel model = mock(AgentModel.class);
        when(model.call(any())).thenReturn(okResponse("ok"));
        CliAgentNode node = CliAgentNode.builder()
                .model(model).workingDirectory(WORKDIR)
                .fixedGoal("Run the migration")
                .build();

        node.execute(AgentContext.of("anything"));

        ArgumentCaptor<AgentTaskRequest> captor = ArgumentCaptor.forClass(AgentTaskRequest.class);
        verify(model).call(captor.capture());
        assertThat(captor.getValue().goal()).isEqualTo("Run the migration");
    }

    @Test
    void noUserMessageSurfacesAsFailedResult() {
        AgentModel model = mock(AgentModel.class);
        CliAgentNode node = CliAgentNode.builder()
                .model(model).workingDirectory(WORKDIR).build();

        AgentResult result = node.execute(AgentContext.empty());

        assertThat(result.hasError()).isTrue();
        assertThat(result.error().cause()).hasMessageContaining("blank goal");
    }

    @Test
    void runtimeExceptionFromModelSurfacesAsFailedResult() {
        AgentModel model = mock(AgentModel.class);
        when(model.call(any())).thenThrow(new RuntimeException("subprocess crashed"));
        CliAgentNode node = CliAgentNode.builder()
                .name("codex")
                .model(model).workingDirectory(WORKDIR).build();

        AgentResult result = node.execute(AgentContext.of("do thing"));

        AgentError error = Objects.requireNonNull(result.error(), "expected error");
        assertThat(error.nodeName()).isEqualTo("codex");
        assertThat(error.cause()).hasMessageContaining("subprocess crashed");
    }

    @Test
    void builderRejectsMissingFields() {
        assertThatThrownBy(() -> CliAgentNode.builder().workingDirectory(WORKDIR).build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("model");
        assertThatThrownBy(() -> CliAgentNode.builder().model(mock(AgentModel.class)).build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("workingDirectory");
    }
}
