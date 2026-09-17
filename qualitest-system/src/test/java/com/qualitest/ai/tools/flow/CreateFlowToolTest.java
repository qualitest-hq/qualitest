package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.flow.model.GraphSchemaVersions;
import com.qualitest.project.domain.TestFlow;
import com.qualitest.project.service.ITestFlowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 测 CreateFlowTool：缺名失败；成功插入空画布并回执 testFlowId。
 * 边界：Mock ITestFlowService，无 DB。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=CreateFlowToolTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CreateFlowToolTest {

    private static final Long PROJECT_ID = 100L;

    private ITestFlowService testFlowService;
    private CreateFlowTool tool;
    private FlowDesignToolContext ctx;

    @BeforeEach
    void setUp() {
        testFlowService = mock(ITestFlowService.class);
        tool = new CreateFlowTool(testFlowService);
        ctx = FlowDesignToolContext.builder().testProjectId(PROJECT_ID).build();
        when(testFlowService.insertTestFlow(any(TestFlow.class))).thenReturn(1);
    }

    /**
     * 前提：未传 flowName。
     * 期望：回执含 error；不调用 insert。
     */
    @Test
    @Order(1)
    @DisplayName("缺 flowName 返回 error")
    void execute_missingFlowName_returnsError() {
        String json = tool.execute(Map.of(), ctx);
        assertTrue(json.contains("error"));
        verify(testFlowService, never()).insertTestFlow(any());
    }

    /**
     * 前提：合法 flowName；insert 成功。
     * 期望：回执含 testFlowId / flowName；落库 graphJson 为空图结构。
     */
    @Test
    @Order(2)
    @DisplayName("成功新建空流并回执 testFlowId")
    void execute_ok_insertsEmptyGraph() {
        String json = tool.execute(Map.of(
                "flowName", "教室创建",
                "flowDescription", "冒烟"
        ), ctx);
        JSONObject result = JSON.parseObject(json);
        assertFalse(result.containsKey("error"));
        assertNotNull(result.getString("testFlowId"));
        assertEquals("教室创建", result.getString("flowName"));
        assertEquals("冒烟", result.getString("flowDescription"));

        ArgumentCaptor<TestFlow> captor = ArgumentCaptor.forClass(TestFlow.class);
        verify(testFlowService).insertTestFlow(captor.capture());
        TestFlow saved = captor.getValue();
        assertEquals(PROJECT_ID, saved.getTestProjectId());
        assertEquals("教室创建", saved.getFlowName());
        assertEquals(result.getString("testFlowId"), String.valueOf(saved.getTestFlowId()));

        JSONObject graph = JSON.parseObject(saved.getGraphJson());
        assertTrue(graph.getJSONArray("nodes").isEmpty());
        assertTrue(graph.getJSONArray("edges").isEmpty());
        JSONObject meta = graph.getJSONObject("meta");
        assertEquals(GraphSchemaVersions.CURRENT, meta.getIntValue("schemaVersion"));
        JSONArray scenarios = meta.getJSONArray("scenarios");
        assertEquals(1, scenarios.size());
        assertEquals("默认（冒烟）", scenarios.getJSONObject(0).getString("name"));
        assertEquals(scenarios.getJSONObject(0).getString("id"), meta.getString("activeScenarioId"));
    }

    /**
     * 前提：缺少 testProjectId。
     * 期望：error；不写库。
     */
    @Test
    @Order(3)
    @DisplayName("缺项目 id 返回 error")
    void execute_missingProject_returnsError() {
        FlowDesignToolContext noProject = FlowDesignToolContext.builder().build();
        String json = tool.execute(Map.of("flowName", "x"), noProject);
        assertTrue(json.contains("error"));
        verify(testFlowService, never()).insertTestFlow(any());
    }
}
