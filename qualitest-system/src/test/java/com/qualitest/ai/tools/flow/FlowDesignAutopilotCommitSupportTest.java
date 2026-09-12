package com.qualitest.ai.tools.flow;

import com.qualitest.ai.scenario.flow.FlowDesignPatchNormalizer;
import com.qualitest.ai.scenario.flow.model.DesignValidationResult;
import com.qualitest.ai.scenario.flow.model.FlowDesignPatch;
import com.qualitest.ai.tools.FlowDesignSubmitCapture;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.validate.GraphJsonValidator;
import com.qualitest.flow.validate.GraphValidationResult;
import com.qualitest.project.domain.TestFlow;
import com.qualitest.project.service.ITestFlowService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 测 FlowDesignAutopilotCommitSupport：半自动跳过；全自动有单元时写库并清空 capture。
 * 边界：Mock 流服务与校验器，不访问 DB。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=FlowDesignAutopilotCommitSupportTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlowDesignAutopilotCommitSupportTest {

    @Test
    @Order(1)
    @DisplayName("半自动跳过落盘")
    void commit_withoutAutopilot_skips() {
        ITestFlowService flowService = mock(ITestFlowService.class);
        FlowDesignToolContext ctx = FlowDesignToolContext.builder()
                .testFlowId(1L)
                .testProjectId(2L)
                .autopilotEnabled(false)
                .build();

        var outcome = FlowDesignAutopilotCommitSupport.commitIfNeeded(
                ctx, flowService, mock(GraphJsonValidator.class), mock(FlowDesignPatchNormalizer.class));

        assertTrue(outcome.ok());
        assertFalse(outcome.committed());
        verify(flowService, never()).updateTestFlow(any());
    }

    @Test
    @Order(2)
    @DisplayName("全自动且有单元时写库并清空 capture")
    void commit_withAcceptedUnits_commitsAndClears() {
        ITestFlowService flowService = mock(ITestFlowService.class);
        GraphJsonValidator validator = mock(GraphJsonValidator.class);
        FlowDesignPatchNormalizer normalizer = mock(FlowDesignPatchNormalizer.class);
        when(validator.validate(any(), any())).thenReturn(GraphValidationResult.of(List.of(), List.of()));
        when(normalizer.apiResolver()).thenReturn(id -> null);
        TestFlow existing = new TestFlow();
        existing.setTestFlowId(3001L);
        existing.setTestProjectId(100L);
        existing.setDelStatus(0);
        when(flowService.selectTestFlowById(3001L)).thenReturn(existing);
        when(flowService.updateTestFlow(any())).thenReturn(1);

        FlowDesignSubmitCapture capture = new FlowDesignSubmitCapture();
        FlowDesignPatch unit = new FlowDesignPatch();
        GraphNode node = GraphNode.builder().id("n1").type("delay").build();
        unit.getAddNodes().add(node);
        capture.record(new FlowDesignPatchNormalizer.NormalizeResult(
                unit,
                DesignValidationResult.builder().ok(true).errors(List.of()).warnings(List.of()).build()));

        GraphJson working = GraphJson.builder().nodes(List.of(node)).build();
        boolean[] committed = {false};
        FlowDesignToolContext ctx = FlowDesignToolContext.builder()
                .testFlowId(3001L)
                .testProjectId(100L)
                .autopilotEnabled(true)
                .submitCapture(capture)
                .graphJson(GraphJson.builder().build())
                .workingGraphRef(new AtomicReference<>(working))
                .onGraphCommitted((id, g) -> committed[0] = true)
                .build();

        var outcome = FlowDesignAutopilotCommitSupport.commitIfNeeded(
                ctx, flowService, validator, normalizer);

        assertTrue(outcome.ok());
        assertTrue(outcome.committed());
        assertFalse(capture.hasAccepted());
        assertTrue(committed[0]);
        verify(flowService, times(1)).updateTestFlow(any());
    }
}
