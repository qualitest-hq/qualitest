package com.qualitest.ai.tools.flow;

import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.project.service.ITestFlowRunService;
import com.qualitest.project.service.ITestFlowRunStepService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * 测 McpGetRunFailureTool：无 runId 时明确提示 MCP 须传 runId。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=McpGetRunFailureToolTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class McpGetRunFailureToolTest {

    /**
     * 前提：arguments 与 context 均无 runId。
     * 期望：error 文案提示须传入 runId；不查库。
     */
    @Test
    @Order(1)
    @DisplayName("缺 runId 明确提示须传入")
    void execute_missingRunId_errors() {
        ITestFlowRunService runService = mock(ITestFlowRunService.class);
        McpGetRunFailureTool tool = new McpGetRunFailureTool(runService, mock(ITestFlowRunStepService.class));
        FlowDesignToolContext ctx = FlowDesignToolContext.builder().testProjectId(1L).build();

        String json = tool.execute(Map.of(), ctx);
        assertTrue(json.contains("缺少 runId"));
        assertTrue(json.contains("请传入 runId"));
        verify(runService, never()).selectTestFlowRunResult(org.mockito.ArgumentMatchers.any());
    }
}
