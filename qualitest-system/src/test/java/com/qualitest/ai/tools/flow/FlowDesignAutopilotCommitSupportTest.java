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
import static org.mockito.ArgumentMatchers.argThat;
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
        existing.setGraphRevision(0L);
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

    /**
     * 前提：工作图有两个无入边节点（完整校验会报双开始节点）。
     * 期望：仍写库；warnings 含开始节点提示；不因完整结构错误失败。
     */
    @Test
    @Order(3)
    @DisplayName("双开始节点仅 warnings，仍落盘")
    void commit_twoStartNodes_commitsWithWarnings() {
        ITestFlowService flowService = mock(ITestFlowService.class);
        GraphJsonValidator validator = mock(GraphJsonValidator.class);
        FlowDesignPatchNormalizer normalizer = mock(FlowDesignPatchNormalizer.class);
        when(validator.validate(any(), argThat(opt -> opt != null && opt.isPersistMinimalOnly())))
                .thenReturn(GraphValidationResult.of(List.of(), List.of()));
        when(validator.validate(any(), argThat(opt -> opt != null && !opt.isPersistMinimalOnly())))
                .thenReturn(GraphValidationResult.of(
                        List.of("流程只能有一个开始节点，当前有 2 个：客户端探活、凭证是否已存在"),
                        List.of()));
        when(normalizer.apiResolver()).thenReturn(id -> null);
        TestFlow existing = new TestFlow();
        existing.setTestFlowId(3002L);
        existing.setTestProjectId(100L);
        existing.setDelStatus(0);
        existing.setGraphRevision(0L);
        when(flowService.selectTestFlowById(3002L)).thenReturn(existing);
        when(flowService.updateTestFlow(any())).thenReturn(1);

        FlowDesignSubmitCapture capture = new FlowDesignSubmitCapture();
        FlowDesignPatch unit = new FlowDesignPatch();
        GraphNode a = GraphNode.builder().id("a").type("http").build();
        GraphNode b = GraphNode.builder().id("b").type("condition").build();
        unit.getAddNodes().add(a);
        unit.getAddNodes().add(b);
        capture.record(new FlowDesignPatchNormalizer.NormalizeResult(
                unit,
                DesignValidationResult.builder().ok(true).errors(List.of()).warnings(List.of()).build()));

        GraphJson working = GraphJson.builder().nodes(List.of(a, b)).build();
        FlowDesignToolContext ctx = FlowDesignToolContext.builder()
                .testFlowId(3002L)
                .testProjectId(100L)
                .autopilotEnabled(true)
                .submitCapture(capture)
                .graphJson(GraphJson.builder().build())
                .workingGraphRef(new AtomicReference<>(working))
                .build();

        var outcome = FlowDesignAutopilotCommitSupport.commitIfNeeded(
                ctx, flowService, validator, normalizer);

        assertTrue(outcome.ok());
        assertTrue(outcome.committed());
        assertTrue(outcome.warnings().stream().anyMatch(w -> w.contains("开始节点")));
        verify(flowService, times(1)).updateTestFlow(any());
    }

    @Test
    @Order(4)
    @DisplayName("版本冲突时重放并重试成功")
    void commit_revisionConflict_replaysAndSucceeds() {
        ITestFlowService flowService = mock(ITestFlowService.class);
        GraphJsonValidator validator = mock(GraphJsonValidator.class);
        FlowDesignPatchNormalizer normalizer = mock(FlowDesignPatchNormalizer.class);
        when(validator.validate(any(), any())).thenReturn(GraphValidationResult.of(List.of(), List.of()));
        when(normalizer.apiResolver()).thenReturn(id -> null);

        GraphNode node = GraphNode.builder().id("n1").type("delay").build();
        GraphJson baseGraph = GraphJson.builder().nodes(List.of()).build();
        GraphJson mergedGraph = GraphJson.builder().nodes(List.of(node)).build();

        TestFlow existing = new TestFlow();
        existing.setTestFlowId(3003L);
        existing.setTestProjectId(100L);
        existing.setDelStatus(0);
        existing.setGraphRevision(0L);
        existing.setGraphJson(baseGraph.toJsonString());

        TestFlow afterConflict = new TestFlow();
        afterConflict.setTestFlowId(3003L);
        afterConflict.setTestProjectId(100L);
        afterConflict.setDelStatus(0);
        afterConflict.setGraphRevision(1L);
        afterConflict.setGraphJson(baseGraph.toJsonString());

        when(flowService.selectTestFlowById(3003L))
                .thenReturn(existing, afterConflict, afterConflict);
        when(flowService.updateTestFlow(any()))
                .thenThrow(new com.qualitest.flow.sync.FlowGraphRevisionConflictException(1L))
                .thenReturn(1);

        FlowDesignPatch unit = new FlowDesignPatch();
        unit.getAddNodes().add(node);
        FlowDesignSubmitCapture capture = new FlowDesignSubmitCapture();
        capture.record(new FlowDesignPatchNormalizer.NormalizeResult(
                unit,
                DesignValidationResult.builder().ok(true).errors(List.of()).warnings(List.of()).build()));

        when(normalizer.normalize(any(), any(), any()))
                .thenReturn(new FlowDesignPatchNormalizer.NormalizeResult(
                        unit,
                        DesignValidationResult.builder().ok(true).errors(List.of()).warnings(List.of()).build()));
        when(normalizer.mergeOnto(any(), any(), any())).thenReturn(mergedGraph);

        GraphJson working = GraphJson.builder().nodes(List.of(node)).build();
        FlowDesignToolContext ctx = FlowDesignToolContext.builder()
                .testFlowId(3003L)
                .testProjectId(100L)
                .autopilotEnabled(true)
                .submitCapture(capture)
                .graphJson(GraphJson.builder().build())
                .workingGraphRef(new AtomicReference<>(working))
                .baseGraphRevisionRef(new AtomicReference<>(0L))
                .build();

        var outcome = FlowDesignAutopilotCommitSupport.commitIfNeeded(
                ctx, flowService, validator, normalizer);

        assertTrue(outcome.ok());
        assertTrue(outcome.committed());
        verify(flowService, times(2)).updateTestFlow(any());
        verify(normalizer, atLeastOnce()).normalize(any(), any(), any());
    }
}
