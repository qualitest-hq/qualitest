package com.qualitest.ai.tools.flow;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.flow.model.GraphFlowOutput;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphMeta;
import com.qualitest.project.domain.TestFlow;
import com.qualitest.project.result.TestFlowResult;
import com.qualitest.project.service.ITestFlowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 测更新测试流元数据工具。
 * 边界：只改名称或说明、清空说明、整表替换对外产出声明、非法入参；Mock 服务层，无数据库。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=UpdateFlowMetaToolTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UpdateFlowMetaToolTest {

    private static final Long PROJECT_ID = 100L;
    private static final Long FLOW_ID = 200L;

    private ITestFlowService testFlowService;
    private UpdateFlowMetaTool tool;
    private FlowDesignToolContext ctx;

    @BeforeEach
    void setUp() {
        testFlowService = mock(ITestFlowService.class);
        tool = new UpdateFlowMetaTool(testFlowService);
        ctx = FlowDesignToolContext.builder().testProjectId(PROJECT_ID).build();
        TestFlowResult existing = new TestFlowResult();
        existing.setTestFlowId(FLOW_ID);
        existing.setTestProjectId(PROJECT_ID);
        existing.setFlowName("旧名");
        existing.setFlowDescription("旧说明");
        when(testFlowService.selectTestFlowResult(FLOW_ID)).thenReturn(existing);
        when(testFlowService.updateTestFlow(any(TestFlow.class))).thenReturn(1);
    }

    /**
     * 前提：未传 testFlowId，上下文也无流 id。
     * 期望：error；不调用 update。
     */
    @Test
    @Order(1)
    @DisplayName("缺 testFlowId 返回 error")
    void execute_missingFlowId_returnsError() {
        String json = tool.execute(Map.of("flowName", "新名"), ctx);
        assertTrue(json.contains("error"));
        verify(testFlowService, never()).updateTestFlow(any());
    }

    /**
     * 前提：有 testFlowId，但未传 flowName 与 flowDescription。
     * 期望：error；不写库。
     */
    @Test
    @Order(2)
    @DisplayName("两字段皆缺返回 error")
    void execute_noMetaFields_returnsError() {
        String json = tool.execute(Map.of("testFlowId", String.valueOf(FLOW_ID)), ctx);
        assertTrue(json.contains("error"));
        verify(testFlowService, never()).updateTestFlow(any());
    }

    /**
     * 前提：只传 flowName。
     * 期望：只更新名称；flowDescription 为 null；无 graphJson；回执说明仍为旧值。
     */
    @Test
    @Order(3)
    @DisplayName("只改 flowName")
    void execute_nameOnly_updatesName() {
        String json = tool.execute(Map.of(
                "testFlowId", String.valueOf(FLOW_ID),
                "flowName", "新名"
        ), ctx);
        JSONObject result = JSON.parseObject(json);
        assertFalse(result.containsKey("error"));
        assertEquals("新名", result.getString("flowName"));
        assertEquals("旧说明", result.getString("flowDescription"));

        ArgumentCaptor<TestFlow> captor = ArgumentCaptor.forClass(TestFlow.class);
        verify(testFlowService).updateTestFlow(captor.capture());
        TestFlow saved = captor.getValue();
        assertEquals(FLOW_ID, saved.getTestFlowId());
        assertEquals("新名", saved.getFlowName());
        assertNull(saved.getFlowDescription());
        assertNull(saved.getGraphJson());
    }

    /**
     * 前提：只传 flowDescription 空串。
     * 期望：说明被清空；不改名称。
     */
    @Test
    @Order(4)
    @DisplayName("flowDescription 空串清空")
    void execute_emptyDescription_clears() {
        Map<String, Object> args = new HashMap<>();
        args.put("testFlowId", String.valueOf(FLOW_ID));
        args.put("flowDescription", "");
        String json = tool.execute(args, ctx);
        JSONObject result = JSON.parseObject(json);
        assertFalse(result.containsKey("error"));
        assertEquals("旧名", result.getString("flowName"));
        assertEquals("", result.getString("flowDescription"));

        ArgumentCaptor<TestFlow> captor = ArgumentCaptor.forClass(TestFlow.class);
        verify(testFlowService).updateTestFlow(captor.capture());
        TestFlow saved = captor.getValue();
        assertNull(saved.getFlowName());
        assertEquals("", saved.getFlowDescription());
        assertNull(saved.getGraphJson());
    }

    /**
     * 前提：flowName 传空串。
     * 期望：error；不写库。
     */
    @Test
    @Order(5)
    @DisplayName("空 flowName 返回 error")
    void execute_blankFlowName_returnsError() {
        Map<String, Object> args = new HashMap<>();
        args.put("testFlowId", String.valueOf(FLOW_ID));
        args.put("flowName", "  ");
        String json = tool.execute(args, ctx);
        assertTrue(json.contains("error"));
        verify(testFlowService, never()).updateTestFlow(any());
    }

    /**
     * 前提：流属于其他项目。
     * 期望：error；不写库。
     */
    @Test
    @Order(6)
    @DisplayName("跨项目流拒绝")
    void execute_wrongProject_returnsError() {
        TestFlowResult other = new TestFlowResult();
        other.setTestFlowId(FLOW_ID);
        other.setTestProjectId(999L);
        when(testFlowService.selectTestFlowResult(FLOW_ID)).thenReturn(other);

        String json = tool.execute(Map.of(
                "testFlowId", String.valueOf(FLOW_ID),
                "flowName", "新名"
        ), ctx);
        assertTrue(json.contains("error"));
        verify(testFlowService, never()).updateTestFlow(any());
    }

    /**
     * 前提：只传 flowOutputs 数组。
     * 期望：调用 replaceFlowOutputs；不改名称；回执含 flowOutputs。
     */
    @Test
    @Order(7)
    @DisplayName("只改 flowOutputs")
    @SuppressWarnings("unchecked")
    void execute_flowOutputsOnly_replacesOutputs() {
        GraphJson saved = GraphJson.builder()
                .meta(GraphMeta.builder()
                        .flowOutputs(List.of(GraphFlowOutput.builder()
                                .name("token")
                                .description("访问令牌")
                                .build()))
                        .build())
                .build();
        when(testFlowService.replaceFlowOutputs(eq(FLOW_ID), eq(PROJECT_ID), anyList(), any()))
                .thenReturn(new ITestFlowService.ApplyGraphPatchResult(saved, 2L, List.of()));

        Map<String, Object> args = new HashMap<>();
        args.put("testFlowId", String.valueOf(FLOW_ID));
        args.put("flowOutputs", List.of(Map.of("name", "token", "description", "访问令牌")));
        String json = tool.execute(args, ctx);
        JSONObject result = JSON.parseObject(json);
        assertFalse(result.containsKey("error"), json);
        assertEquals("旧名", result.getString("flowName"));
        assertEquals("token", result.getJSONArray("flowOutputs").getJSONObject(0).getString("name"));
        assertEquals("访问令牌", result.getJSONArray("flowOutputs").getJSONObject(0).getString("description"));

        verify(testFlowService, never()).updateTestFlow(any());
        ArgumentCaptor<List<GraphFlowOutput>> captor = ArgumentCaptor.forClass(List.class);
        verify(testFlowService).replaceFlowOutputs(eq(FLOW_ID), eq(PROJECT_ID), captor.capture(), any());
        assertEquals(1, captor.getValue().size());
        assertEquals("token", captor.getValue().get(0).getName());
    }

    /**
     * 前提：flowOutputs 不是数组。
     * 期望：error；不写库。
     */
    @Test
    @Order(8)
    @DisplayName("flowOutputs 非数组返回 error")
    void execute_flowOutputsNotArray_returnsError() {
        Map<String, Object> args = new HashMap<>();
        args.put("testFlowId", String.valueOf(FLOW_ID));
        args.put("flowOutputs", "token");
        String json = tool.execute(args, ctx);
        assertTrue(json.contains("error"));
        verify(testFlowService, never()).updateTestFlow(any());
        verify(testFlowService, never()).replaceFlowOutputs(any(), any(), any(), any());
    }
}
