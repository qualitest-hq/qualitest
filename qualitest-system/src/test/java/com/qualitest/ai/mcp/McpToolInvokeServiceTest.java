package com.qualitest.ai.mcp;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.FlowDesignToolContextFactory;
import com.qualitest.ai.tools.FlowDesignToolExecutor;
import com.qualitest.api.params.McpToolInvokeParams;
import com.qualitest.api.result.McpToolResult;
import com.qualitest.common.exception.ServiceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 测 McpToolInvokeService：白名单拦截、拒绝 submit_patch、只读工具委托与 error 标记。
 * 边界：Executor/ContextFactory Mock；无真实 LLM / DB。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=McpToolInvokeServiceTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class McpToolInvokeServiceTest {

    private FlowDesignToolExecutor toolExecutor;
    private FlowDesignToolContextFactory contextFactory;
    private McpToolInvokeService service;

    @BeforeEach
    void setUp() {
        toolExecutor = mock(FlowDesignToolExecutor.class);
        contextFactory = mock(FlowDesignToolContextFactory.class);
        service = new McpToolInvokeService(toolExecutor, contextFactory);
    }

    /**
     * 前提：工具名 unknown（非白名单）。
     * 期望：抛 ServiceException。
     */
    @Test
    @Order(1)
    @DisplayName("未知工具名抛 ServiceException")
    void invoke_unknownTool_throws() {
        McpToolInvokeParams params = new McpToolInvokeParams();
        assertThrows(ServiceException.class, () -> service.invoke("unknown", params, 100L));
    }

    /**
     * 前提：调用 submit_add_http_node。
     * 期望：抛 ServiceException（含「不支持修改测试流」）；不委托 Executor。
     */
    @Test
    @Order(2)
    @DisplayName("submit 单元工具被拒绝")
    void invoke_submitUnit_rejected() {
        McpToolInvokeParams params = new McpToolInvokeParams();
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.invoke(com.qualitest.ai.tools.FlowDesignToolNames.SUBMIT_ADD_HTTP_NODE.getId(), params, 100L));
        assertTrue(ex.getMessage().contains("不支持修改测试流"));
    }

    /**
     * 前提：白名单工具 list_flows。
     * 期望：经 ContextFactory 后调 Executor；result.tool=list_flows。
     */
    @Test
    @Order(3)
    @DisplayName("list_flows 白名单通过并委托")
    void invoke_listFlows_allowed() {
        McpToolInvokeParams params = new McpToolInvokeParams();
        FlowDesignToolContext ctx = FlowDesignToolContext.builder()
                .testProjectId(100L)
                .maxSearchApis(10)
                .maxToolResultBytes(8192)
                .build();
        when(contextFactory.fromMcpRequest(eq(params), eq(100L))).thenReturn(ctx);
        when(toolExecutor.executeTool(eq(FlowDesignToolExecutor.LIST_FLOWS), anyString(), eq(ctx)))
                .thenReturn("{\"items\":[]}");

        McpToolResult result = service.invoke(FlowDesignToolExecutor.LIST_FLOWS, params, 100L);
        assertEquals(FlowDesignToolExecutor.LIST_FLOWS, result.getTool());
    }

    /**
     * 前提：search_apis，arguments.keyword=login。
     * 期望：resultJson 可解析且含 items。
     */
    @Test
    @Order(4)
    @DisplayName("search_apis 返回含 items 的结果")
    void invoke_searchApis_returnsResult() {
        McpToolInvokeParams params = new McpToolInvokeParams();
        params.setArguments(Map.of("keyword", "login"));
        FlowDesignToolContext ctx = FlowDesignToolContext.builder()
                .testProjectId(100L)
                .maxSearchApis(10)
                .maxToolResultBytes(8192)
                .build();
        when(contextFactory.fromMcpRequest(eq(params), eq(100L))).thenReturn(ctx);
        when(toolExecutor.executeTool(eq(FlowDesignToolExecutor.SEARCH_APIS), anyString(), eq(ctx)))
                .thenReturn("{\"items\":[],\"truncated\":false}");

        McpToolResult result = service.invoke(FlowDesignToolExecutor.SEARCH_APIS, params, 100L);

        assertEquals(FlowDesignToolExecutor.SEARCH_APIS, result.getTool());
        JSONObject json = JSON.parseObject(result.getResultJson());
        assertTrue(json.containsKey("items"));
    }

    /**
     * 前提：list_subflow_templates。
     * 期望：tool 名正确；resultJson 含 platformTemplates、projectSubflows。
     */
    @Test
    @Order(5)
    @DisplayName("list_subflow_templates 返回模板列表")
    void invoke_listSubflowTemplates_allowed() {
        McpToolInvokeParams params = new McpToolInvokeParams();
        FlowDesignToolContext ctx = FlowDesignToolContext.builder()
                .testProjectId(100L)
                .maxSearchApis(10)
                .maxToolResultBytes(8192)
                .build();
        when(contextFactory.fromMcpRequest(eq(params), eq(100L))).thenReturn(ctx);
        when(toolExecutor.executeTool(eq(FlowDesignToolExecutor.LIST_SUBFLOW_TEMPLATES), anyString(), eq(ctx)))
                .thenReturn("{\"platformTemplates\":[{\"templateId\":\"tpl_oauth_client_credentials\"}],\"projectSubflows\":[]}");

        McpToolResult result = service.invoke(FlowDesignToolExecutor.LIST_SUBFLOW_TEMPLATES, params, 100L);

        assertEquals(FlowDesignToolExecutor.LIST_SUBFLOW_TEMPLATES, result.getTool());
        JSONObject json = JSON.parseObject(result.getResultJson());
        assertTrue(json.containsKey("platformTemplates"));
        assertTrue(json.containsKey("projectSubflows"));
        assertEquals("tpl_oauth_client_credentials",
                json.getJSONArray("platformTemplates").getJSONObject(0).getString("templateId"));
    }

    /**
     * 前提：get_subflow_detail。
     * 期望：tool 名正确；resultJson 含 testFlowId、nodeCount。
     */
    @Test
    @Order(6)
    @DisplayName("get_subflow_detail 返回节点边计数")
    void invoke_getSubflowDetail_allowed() {
        McpToolInvokeParams params = new McpToolInvokeParams();
        params.setArguments(Map.of("testFlowId", "3002"));
        FlowDesignToolContext ctx = FlowDesignToolContext.builder()
                .testProjectId(100L)
                .maxSearchApis(10)
                .maxToolResultBytes(8192)
                .build();
        when(contextFactory.fromMcpRequest(eq(params), eq(100L))).thenReturn(ctx);
        when(toolExecutor.executeTool(eq(FlowDesignToolExecutor.GET_SUBFLOW_DETAIL), anyString(), eq(ctx)))
                .thenReturn("{\"testFlowId\":\"3002\",\"nodeCount\":2,\"edgeCount\":1}");

        McpToolResult result = service.invoke(FlowDesignToolExecutor.GET_SUBFLOW_DETAIL, params, 100L);

        assertEquals(FlowDesignToolExecutor.GET_SUBFLOW_DETAIL, result.getTool());
        JSONObject json = JSON.parseObject(result.getResultJson());
        assertEquals("3002", json.getString("testFlowId"));
        assertEquals(2, json.getIntValue("nodeCount"));
        assertEquals(1, json.getIntValue("edgeCount"));
    }

    /**
     * 前提：get_flow。
     * 期望：委托 Executor 并返回 resultJson。
     */
    @Test
    @Order(7)
    @DisplayName("get_flow 允许并返回结果")
    void invoke_getFlow_allowed() {
        McpToolInvokeParams params = new McpToolInvokeParams();
        params.setArguments(Map.of("testFlowId", "3001"));
        FlowDesignToolContext ctx = FlowDesignToolContext.builder()
                .testProjectId(100L)
                .build();
        when(contextFactory.fromMcpRequest(eq(params), eq(100L))).thenReturn(ctx);
        when(toolExecutor.executeTool(eq(FlowDesignToolExecutor.GET_FLOW), anyString(), eq(ctx)))
                .thenReturn("{\"testFlowId\":\"3001\",\"graphJson\":{}}");

        McpToolResult result = service.invoke(FlowDesignToolExecutor.GET_FLOW, params, 100L);

        assertEquals(FlowDesignToolExecutor.GET_FLOW, result.getTool());
        assertFalse(result.isError());
    }

    /**
     * 前提：工具返回 JSON 含 error 字段。
     * 期望：McpToolResult.error=true。
     */
    @Test
    @Order(8)
    @DisplayName("业务 error 字段标记 isError")
    void invoke_businessError_setsErrorFlag() {
        McpToolInvokeParams params = new McpToolInvokeParams();
        FlowDesignToolContext ctx = FlowDesignToolContext.builder()
                .testProjectId(100L)
                .build();
        when(contextFactory.fromMcpRequest(eq(params), eq(100L))).thenReturn(ctx);
        when(toolExecutor.executeTool(eq(FlowDesignToolExecutor.GET_FLOW), anyString(), eq(ctx)))
                .thenReturn("{\"error\":\"测试流不存在\"}");

        McpToolResult result = service.invoke(FlowDesignToolExecutor.GET_FLOW, params, 100L);

        assertTrue(result.isError());
    }
}
