package com.qualitest.ai.tools.flow;

import com.qualitest.ai.scenario.flow.FlowDesignPatchNormalizer;
import com.qualitest.ai.scenario.flow.model.DesignValidationResult;
import com.qualitest.ai.scenario.flow.model.FlowDesignPatch;
import com.qualitest.ai.tools.FlowDesignSubmitCapture;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.sync.FlowEditLeaseConflictException;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 测 FlowDesignAutopilotCommitSupport：半自动跳过落盘；全自动合并 capture 后增量写图。
 * 边界：Mock 流服务，不访问 DB。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=FlowDesignAutopilotCommitSupportTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlowDesignAutopilotCommitSupportTest {

    /**
     * 前提：未开启全自动。
     * 期望：返回 ok 且未落盘，不调用 applyGraphPatch。
     */
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

        var outcome = FlowDesignAutopilotCommitSupport.commitIfNeeded(ctx, flowService);

        assertTrue(outcome.ok());
        assertFalse(outcome.committed());
        verify(flowService, never()).applyGraphPatch(any(), any(), any(), any());
    }

    /**
     * 前提：全自动开启，capture 中有已接受单元，上下文带写锁端标识。
     * 期望：增量写库成功，清空 capture，回调 onGraphCommitted，基线版本号更新。
     */
    @Test
    @Order(2)
    @DisplayName("全自动且有单元时增量写库并清空 capture")
    void commit_withAcceptedUnits_commitsAndClears() {
        ITestFlowService flowService = mock(ITestFlowService.class);
        GraphNode node = GraphNode.builder().id("n1").type("delay").build();
        GraphJson saved = GraphJson.builder().nodes(List.of(node)).build();
        when(flowService.applyGraphPatch(eq(3001L), eq(100L), any(), any()))
                .thenReturn(new ITestFlowService.ApplyGraphPatchResult(saved, 1L, List.of()));

        TestFlow existing = new TestFlow();
        existing.setTestFlowId(3001L);
        existing.setTestProjectId(100L);
        existing.setDelStatus(0);
        when(flowService.selectTestFlowById(3001L)).thenReturn(existing);

        FlowDesignSubmitCapture capture = new FlowDesignSubmitCapture();
        FlowDesignPatch unit = new FlowDesignPatch();
        unit.getAddNodes().add(node);
        capture.record(new FlowDesignPatchNormalizer.NormalizeResult(
                unit,
                DesignValidationResult.builder().ok(true).errors(List.of()).warnings(List.of()).build()));

        boolean[] committed = {false};
        FlowDesignToolContext ctx = FlowDesignToolContext.builder()
                .testFlowId(3001L)
                .testProjectId(100L)
                .autopilotEnabled(true)
                .submitCapture(capture)
                .graphJson(GraphJson.builder().build())
                .workingGraphRef(new AtomicReference<>(saved))
                .onGraphCommitted((id, g) -> committed[0] = true)
                .build();
        ctx.setEditLeaseEndpoint("mcp:test-session");

        var outcome = FlowDesignAutopilotCommitSupport.commitIfNeeded(ctx, flowService);

        assertTrue(outcome.ok());
        assertTrue(outcome.committed());
        assertFalse(capture.hasAccepted());
        assertTrue(committed[0]);
        assertEquals(1L, ctx.getBaseGraphRevision());
        verify(flowService, times(1)).applyGraphPatch(eq(3001L), eq(100L), any(), eq("mcp:test-session"));
    }

    /**
     * 前提：全自动，合并 patch 后校验产生「多开始节点」类 warnings。
     * 期望：仍落盘成功，outcome 携带对应 warning 文案。
     */
    @Test
    @Order(3)
    @DisplayName("合并后双开始节点带 warnings 仍落盘")
    void commit_twoStartNodes_commitsWithWarnings() {
        ITestFlowService flowService = mock(ITestFlowService.class);
        GraphNode a = GraphNode.builder().id("a").type("http").build();
        GraphNode b = GraphNode.builder().id("b").type("condition").build();
        GraphJson saved = GraphJson.builder().nodes(List.of(a, b)).build();
        when(flowService.applyGraphPatch(any(), any(), any(), any()))
                .thenReturn(new ITestFlowService.ApplyGraphPatchResult(
                        saved, 2L, List.of("流程只能有一个开始节点，当前有 2 个：客户端探活、凭证是否已存在")));

        TestFlow existing = new TestFlow();
        existing.setTestFlowId(3002L);
        existing.setTestProjectId(100L);
        existing.setDelStatus(0);
        when(flowService.selectTestFlowById(3002L)).thenReturn(existing);

        FlowDesignSubmitCapture capture = new FlowDesignSubmitCapture();
        FlowDesignPatch unit = new FlowDesignPatch();
        unit.getAddNodes().add(a);
        unit.getAddNodes().add(b);
        capture.record(new FlowDesignPatchNormalizer.NormalizeResult(
                unit,
                DesignValidationResult.builder().ok(true).errors(List.of()).warnings(List.of()).build()));

        FlowDesignToolContext ctx = FlowDesignToolContext.builder()
                .testFlowId(3002L)
                .testProjectId(100L)
                .autopilotEnabled(true)
                .submitCapture(capture)
                .graphJson(GraphJson.builder().build())
                .workingGraphRef(new AtomicReference<>(saved))
                .build();

        var outcome = FlowDesignAutopilotCommitSupport.commitIfNeeded(ctx, flowService);

        assertTrue(outcome.ok());
        assertTrue(outcome.committed());
        assertTrue(outcome.warnings().stream().anyMatch(w -> w.contains("开始节点")));
    }

    /**
     * 前提：全自动，applyGraphPatch 因引用节点已被删而抛业务异常。
     * 期望：outcome 失败、未落盘，capture 仍保留待处理单元。
     */
    @Test
    @Order(4)
    @DisplayName("引用节点已不存在时失败且不写库")
    void commit_missingNode_fails() {
        ITestFlowService flowService = mock(ITestFlowService.class);
        when(flowService.applyGraphPatch(any(), any(), any(), any()))
                .thenThrow(new com.qualitest.common.exception.ServiceException(
                        "节点 x 已不存在，可能已被其他端删除"));

        TestFlow existing = new TestFlow();
        existing.setTestFlowId(3003L);
        existing.setTestProjectId(100L);
        existing.setDelStatus(0);
        when(flowService.selectTestFlowById(3003L)).thenReturn(existing);

        FlowDesignSubmitCapture capture = new FlowDesignSubmitCapture();
        FlowDesignPatch unit = new FlowDesignPatch();
        GraphNode edgeSourceMissing = GraphNode.builder().id("n1").type("delay").build();
        unit.getAddNodes().add(edgeSourceMissing);
        capture.record(new FlowDesignPatchNormalizer.NormalizeResult(
                unit,
                DesignValidationResult.builder().ok(true).errors(List.of()).warnings(List.of()).build()));

        FlowDesignToolContext ctx = FlowDesignToolContext.builder()
                .testFlowId(3003L)
                .testProjectId(100L)
                .autopilotEnabled(true)
                .submitCapture(capture)
                .graphJson(GraphJson.builder().build())
                .workingGraphRef(new AtomicReference<>(GraphJson.builder().build()))
                .build();

        var outcome = FlowDesignAutopilotCommitSupport.commitIfNeeded(ctx, flowService);

        assertFalse(outcome.ok());
        assertFalse(outcome.committed());
        assertTrue(outcome.message().contains("已不存在"));
        assertTrue(capture.hasAccepted());
    }

    /**
     * 前提：全自动，写图时他人持锁，服务端抛写锁冲突。
     * 期望：outcome 失败，lockHeldBy 为占锁方标识。
     */
    @Test
    @Order(5)
    @DisplayName("写锁冲突时返回 lockHeldBy")
    void commit_leaseConflict_returnsHeldBy() {
        ITestFlowService flowService = mock(ITestFlowService.class);
        when(flowService.applyGraphPatch(any(), any(), any(), any()))
                .thenThrow(new FlowEditLeaseConflictException("web:alice:uuid"));

        TestFlow existing = new TestFlow();
        existing.setTestFlowId(3004L);
        existing.setTestProjectId(100L);
        existing.setDelStatus(0);
        when(flowService.selectTestFlowById(3004L)).thenReturn(existing);

        FlowDesignSubmitCapture capture = new FlowDesignSubmitCapture();
        FlowDesignPatch unit = new FlowDesignPatch();
        unit.getAddNodes().add(GraphNode.builder().id("n1").type("delay").build());
        capture.record(new FlowDesignPatchNormalizer.NormalizeResult(
                unit,
                DesignValidationResult.builder().ok(true).errors(List.of()).warnings(List.of()).build()));

        FlowDesignToolContext ctx = FlowDesignToolContext.builder()
                .testFlowId(3004L)
                .testProjectId(100L)
                .autopilotEnabled(true)
                .submitCapture(capture)
                .graphJson(GraphJson.builder().build())
                .workingGraphRef(new AtomicReference<>(GraphJson.builder().build()))
                .build();

        var outcome = FlowDesignAutopilotCommitSupport.commitIfNeeded(ctx, flowService);

        assertFalse(outcome.ok());
        assertEquals("web:alice:uuid", outcome.lockHeldBy());
    }
}
