package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.scenario.flow.FlowDesignPatchNormalizer;
import com.qualitest.ai.scenario.flow.model.DesignValidationResult;
import com.qualitest.ai.scenario.flow.model.FlowDesignPatch;
import com.qualitest.ai.tools.FlowDesignSubmitCapture;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.run.RunStatus;
import com.qualitest.flow.validate.GraphJsonValidator;
import com.qualitest.flow.validate.GraphValidationResult;
import com.qualitest.project.params.TriggerTestFlowRunParams;
import com.qualitest.project.result.TestFlowRunResult;
import com.qualitest.project.service.ITestFlowExecutionService;
import com.qualitest.project.service.ITestFlowRunService;
import com.qualitest.project.service.ITestFlowRunStepService;
import com.qualitest.project.service.ITestFlowService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 测 RunTestFlowTool：全自动触发 Run；有 pending submit 时先隐式落盘。
 * 边界：Mock execution / run / flow 服务，不真实跑流。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=RunTestFlowToolTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RunTestFlowToolTest {

    /**
     * 前提：已开全自动、无 pending capture、trigger 返回 runId、status=passed。
     * 期望：ok、passed=true，未走落盘。
     */
    @Test
    @Order(1)
    @DisplayName("无 pending 时直接 Run 并返回通过摘要")
    void execute_cleanCapture_runsAndReturnsPassed() {
        ITestFlowExecutionService execution = mock(ITestFlowExecutionService.class);
        ITestFlowRunService runService = mock(ITestFlowRunService.class);
        ITestFlowService flowService = mock(ITestFlowService.class);
        when(execution.triggerRun(any(TriggerTestFlowRunParams.class))).thenReturn(88L);
        TestFlowRunResult run = new TestFlowRunResult();
        run.setStatus(RunStatus.PASSED);
        when(runService.selectTestFlowRunResult(88L)).thenReturn(run);

        RunTestFlowTool tool = new RunTestFlowTool(
                execution, runService, mock(ITestFlowRunStepService.class),
                flowService, mock(GraphJsonValidator.class), mock(FlowDesignPatchNormalizer.class));
        FlowDesignToolContext ctx = FlowDesignToolContext.builder()
                .testFlowId(1L)
                .autopilotEnabled(true)
                .submitCapture(new FlowDesignSubmitCapture())
                .build();

        String json = tool.execute(Map.of(), ctx);
        JSONObject root = JSON.parseObject(json);
        assertTrue(root.getBooleanValue("ok"));
        assertTrue(root.getBooleanValue("passed"));
        assertEquals("88", root.getString("runId"));
        assertFalse(root.getBooleanValue("autoCommittedBeforeRun"));
        verify(flowService, never()).updateTestFlow(any());
    }

    /**
     * 前提：半自动。
     * 期望：error，不调 triggerRun。
     */
    @Test
    @Order(2)
    @DisplayName("半自动拒绝 run")
    void execute_withoutAutopilot_rejects() {
        ITestFlowExecutionService execution = mock(ITestFlowExecutionService.class);
        RunTestFlowTool tool = new RunTestFlowTool(
                execution, mock(ITestFlowRunService.class), mock(ITestFlowRunStepService.class),
                mock(ITestFlowService.class), mock(GraphJsonValidator.class),
                mock(FlowDesignPatchNormalizer.class));
        FlowDesignToolContext ctx = FlowDesignToolContext.builder()
                .testFlowId(1L)
                .autopilotEnabled(false)
                .build();

        String json = tool.execute(Map.of(), ctx);
        assertTrue(JSON.parseObject(json).getString("error").contains("半自动"));
        verify(execution, never()).triggerRun(any());
    }

    /**
     * 前提：有已接受单元但校验失败。
     * 期望：跑流前落盘失败，不 triggerRun。
     */
    @Test
    @Order(3)
    @DisplayName("有 pending 且校验失败时不跑流")
    void execute_pendingCaptureValidationFail_doesNotRun() {
        ITestFlowExecutionService execution = mock(ITestFlowExecutionService.class);
        GraphJsonValidator validator = mock(GraphJsonValidator.class);
        when(validator.validate(any(), any())).thenReturn(
                GraphValidationResult.of(List.of("结构错误"), List.of()));
        FlowDesignPatchNormalizer normalizer = mock(FlowDesignPatchNormalizer.class);
        when(normalizer.apiResolver()).thenReturn(id -> null);

        FlowDesignSubmitCapture capture = new FlowDesignSubmitCapture();
        FlowDesignPatch unit = new FlowDesignPatch();
        unit.getAddNodes().add(GraphNode.builder().id("n1").type("delay").build());
        capture.record(new FlowDesignPatchNormalizer.NormalizeResult(
                unit,
                DesignValidationResult.builder().ok(true).errors(List.of()).warnings(List.of()).build()));

        RunTestFlowTool tool = new RunTestFlowTool(
                execution, mock(ITestFlowRunService.class), mock(ITestFlowRunStepService.class),
                mock(ITestFlowService.class), validator, normalizer);
        FlowDesignToolContext ctx = FlowDesignToolContext.builder()
                .testFlowId(1L)
                .testProjectId(100L)
                .autopilotEnabled(true)
                .submitCapture(capture)
                .graphJson(com.qualitest.flow.model.GraphJson.builder().build())
                .workingGraphRef(new java.util.concurrent.atomic.AtomicReference<>(
                        com.qualitest.flow.model.GraphJson.builder()
                                .nodes(List.of(GraphNode.builder().id("n1").type("delay").build()))
                                .build()))
                .build();

        String json = tool.execute(Map.of(), ctx);
        JSONObject root = JSON.parseObject(json);
        assertFalse(root.getBooleanValue("ok"));
        assertTrue(root.getString("error").contains("自动落盘失败"));
        verify(execution, never()).triggerRun(any());
    }
}
