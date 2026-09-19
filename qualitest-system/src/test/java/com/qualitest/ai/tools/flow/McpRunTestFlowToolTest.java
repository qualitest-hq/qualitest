package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.scenario.flow.FlowDesignPatchNormalizer;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.flow.run.RunStatus;
import com.qualitest.flow.validate.GraphJsonValidator;
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
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * 测 McpRunTestFlowTool：须 testFlowId 与 operatorUserId；触发时写入 operatorUserId。
 * 边界：Mock execution，不真实跑流。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=McpRunTestFlowToolTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class McpRunTestFlowToolTest {

    /**
     * 前提：全自动、有 operator 与 testFlowId、trigger 返回 runId、await passed。
     * 期望：ok；TriggerTestFlowRunParams.operatorUserId=7。
     */
    @Test
    @Order(1)
    @DisplayName("带操作者与 testFlowId 可跑流")
    void execute_withOperatorAndFlowId_triggers() {
        ITestFlowExecutionService execution = mock(ITestFlowExecutionService.class);
        when(execution.triggerRun(any(TriggerTestFlowRunParams.class))).thenReturn(88L);
        TestFlowRunResult run = new TestFlowRunResult();
        run.setStatus(RunStatus.PASSED.getCode());
        when(execution.awaitRunTerminal(eq(88L), anyLong())).thenReturn(run);

        McpRunTestFlowTool tool = new McpRunTestFlowTool(
                execution, mock(ITestFlowRunService.class), mock(ITestFlowRunStepService.class),
                mock(ITestFlowService.class), mock(GraphJsonValidator.class),
                mock(FlowDesignPatchNormalizer.class));
        FlowDesignToolContext ctx = FlowDesignToolContext.builder()
                .testFlowId(1L)
                .operatorUserId(7L)
                .autopilotEnabled(true)
                .build();

        String json = tool.execute(Map.of("testFlowId", "1"), ctx);
        JSONObject root = JSON.parseObject(json);
        assertTrue(root.getBooleanValue("ok"));
        ArgumentCaptor<TriggerTestFlowRunParams> captor = ArgumentCaptor.forClass(TriggerTestFlowRunParams.class);
        verify(execution).triggerRun(captor.capture());
        assertEquals(7L, captor.getValue().getOperatorUserId());
        assertEquals(1L, captor.getValue().getTestFlowId());
    }

    /**
     * 前提：缺 operatorUserId。
     * 期望：error 含未绑定操作者；不调 triggerRun。
     */
    @Test
    @Order(2)
    @DisplayName("缺操作者明确报错")
    void execute_missingOperator_errors() {
        ITestFlowExecutionService execution = mock(ITestFlowExecutionService.class);
        McpRunTestFlowTool tool = new McpRunTestFlowTool(
                execution, mock(ITestFlowRunService.class), mock(ITestFlowRunStepService.class),
                mock(ITestFlowService.class), mock(GraphJsonValidator.class),
                mock(FlowDesignPatchNormalizer.class));
        FlowDesignToolContext ctx = FlowDesignToolContext.builder()
                .testFlowId(1L)
                .autopilotEnabled(true)
                .build();

        String json = tool.execute(Map.of("testFlowId", "1"), ctx);
        assertTrue(json.contains("未绑定操作者"));
        verify(execution, never()).triggerRun(any());
    }

    /**
     * 前提：有操作者但无 testFlowId。
     * 期望：error 含缺少 testFlowId。
     */
    @Test
    @Order(3)
    @DisplayName("缺 testFlowId 明确报错")
    void execute_missingTestFlowId_errors() {
        ITestFlowExecutionService execution = mock(ITestFlowExecutionService.class);
        McpRunTestFlowTool tool = new McpRunTestFlowTool(
                execution, mock(ITestFlowRunService.class), mock(ITestFlowRunStepService.class),
                mock(ITestFlowService.class), mock(GraphJsonValidator.class),
                mock(FlowDesignPatchNormalizer.class));
        FlowDesignToolContext ctx = FlowDesignToolContext.builder()
                .operatorUserId(7L)
                .autopilotEnabled(true)
                .build();

        String json = tool.execute(Map.of(), ctx);
        assertTrue(json.contains("缺少 testFlowId"));
        verify(execution, never()).triggerRun(any());
    }
}
