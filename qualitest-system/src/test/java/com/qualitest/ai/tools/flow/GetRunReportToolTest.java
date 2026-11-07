package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.project.params.TestFlowRunStepParams;
import com.qualitest.project.result.TestFlowRunResult;
import com.qualitest.project.result.TestFlowRunStepResult;
import com.qualitest.project.service.ITestFlowRunService;
import com.qualitest.project.service.ITestFlowRunStepService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 测 GetRunReportTool / McpGetRunReportTool。
 * 覆盖：无 nodeId 的时间线摘要、带 nodeId 的单步明细、门面缺 runId 报错。
 * 边界：缺 runId、无匹配节点。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=GetRunReportToolTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GetRunReportToolTest {

    private final ITestFlowRunService runService = mock(ITestFlowRunService.class);
    private final ITestFlowRunStepService stepService = mock(ITestFlowRunStepService.class);
    private final GetRunReportTool tool = new GetRunReportTool(runService, stepService);

    /**
     * 前提：Run 含 http/assert/script 通过步。
     * 期望：mode=timeline；每步有 summary；无 body。
     */
    @Test
    @Order(1)
    @DisplayName("无 nodeId 返回全流时间线摘要")
    void timeline_returnsSummaryPerStep() {
        Long runId = 9001L;
        stubRun(runId);
        when(stepService.selectTestFlowRunStepResultList(any(TestFlowRunStepParams.class)))
                .thenReturn(List.of(
                        step(1, "h1", "http", "登录", "passed",
                                "{\"http\":{\"method\":\"POST\",\"url\":\"http://x/login\",\"status\":200,"
                                        + "\"bizCheck\":{\"passed\":true},\"response\":{\"body\":{\"token\":\"t\"}}}}"),
                        step(2, "a1", "assert", "断言", "passed",
                                "{\"assert\":{\"rules\":[{\"left\":\"http.body.code\",\"operator\":\"eq\","
                                        + "\"right\":\"200\",\"passed\":true}]}}"),
                        step(3, "s1", "script", "脚本", "passed",
                                "{\"script\":{\"language\":\"javascript\",\"logs\":[\"hello\"],\"writes\":[]}}")
                ));

        JSONObject root = JSON.parseObject(tool.execute(
                Map.of("runId", String.valueOf(runId)),
                ctx(runId)));

        assertEquals("timeline", root.getString("mode"));
        assertEquals(3, root.getIntValue("stepCount"));
        JSONArray steps = root.getJSONArray("steps");
        assertEquals(3, steps.size());
        assertTrue(steps.getJSONObject(0).getString("summary").contains("POST"));
        assertTrue(steps.getJSONObject(1).getString("summary").contains("1/1"));
        assertTrue(steps.getJSONObject(2).getString("summary").contains("hello"));
        assertNull(steps.getJSONObject(0).getJSONObject("http"));
    }

    /**
     * 前提：同一 Run；传 nodeId=a1。
     * 期望：mode=detail；含 assert.rules；无需先查时间线。
     */
    @Test
    @Order(2)
    @DisplayName("带 nodeId 直接返回该步明细")
    void detail_byNodeId_returnsRules() {
        Long runId = 9002L;
        stubRun(runId);
        when(stepService.selectTestFlowRunStepResultList(any(TestFlowRunStepParams.class)))
                .thenReturn(List.of(
                        step(1, "h1", "http", "登录", "passed",
                                "{\"http\":{\"method\":\"GET\",\"url\":\"http://x\",\"status\":200}}"),
                        step(2, "a1", "assert", "断言", "passed",
                                "{\"assert\":{\"rules\":[{\"left\":\"http.body.code\",\"operator\":\"eq\","
                                        + "\"right\":\"200\",\"passed\":true,\"leftActual\":200}]}}")
                ));

        JSONObject root = JSON.parseObject(tool.execute(
                Map.of("runId", String.valueOf(runId), "nodeId", "a1"),
                ctx(runId)));

        assertEquals("detail", root.getString("mode"));
        assertEquals("a1", root.getString("nodeId"));
        assertEquals(1, root.getIntValue("stepCount"));
        JSONObject assertObj = root.getJSONArray("steps").getJSONObject(0).getJSONObject("assert");
        assertEquals(1, assertObj.getIntValue("passedCount"));
        assertEquals(1, assertObj.getJSONArray("rules").size());
    }

    /**
     * 前提：门面调用未传 runId，上下文也无运行 ID。
     * 期望：返回含 runId 字样的错误。
     */
    @Test
    @Order(3)
    @DisplayName("MCP 门面缺 runId 报错")
    void mcpFacade_requiresRunId() {
        McpGetRunReportTool mcp = new McpGetRunReportTool(tool);
        JSONObject root = JSON.parseObject(mcp.execute(Map.of(), ctx(null)));
        assertTrue(root.getString("error").contains("runId"));
    }

    /** mock 一条已通过的 Run 头记录 */
    private void stubRun(Long runId) {
        when(runService.selectTestFlowRunResult(runId)).thenReturn(
                TestFlowRunResult.builder()
                        .testFlowRunId(runId)
                        .testFlowId(3001L)
                        .status("passed")
                        .build());
    }

    /** 构造带项目/流/可选上下文运行 ID 的工具上下文 */
    private static FlowDesignToolContext ctx(Long runId) {
        return FlowDesignToolContext.builder()
                .testProjectId(1L)
                .testFlowId(3001L)
                .contextRunId(runId)
                .maxToolResultBytes(8192)
                .build();
    }

    /** 构造单条步骤结果（含 step_details JSON 字符串） */
    private static TestFlowRunStepResult step(
            long index, String nodeId, String type, String name, String status, String details) {
        return TestFlowRunStepResult.builder()
                .stepIndex(index)
                .nodeId(nodeId)
                .nodeType(type)
                .nodeName(name)
                .status(status)
                .durationMs(10L)
                .stepDetails(details)
                .build();
    }
}
