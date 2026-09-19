package com.qualitest.ai.mcp;

import com.qualitest.ai.scenario.flow.FlowDesignPatchNormalizer;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.FlowDesignToolContextFactory;
import com.qualitest.ai.tools.FlowDesignToolExecutor;
import com.qualitest.ai.tools.FlowDesignToolNames;
import com.qualitest.api.params.McpToolInvokeParams;
import com.qualitest.api.result.McpToolResult;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.flow.validate.GraphJsonValidator;
import com.qualitest.project.domain.TestProject;
import com.qualitest.project.service.ITestFlowService;
import com.qualitest.project.service.ITestProjectService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 测 MCP 工具调用：只读白名单、写流开关、导入接口开关、关开关时拒绝、开开关查询、错误回执标记。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class McpToolInvokeServiceTest {

    private FlowDesignToolExecutor toolExecutor;
    private FlowDesignToolContextFactory contextFactory;
    private ITestProjectService testProjectService;
    private ITestFlowService testFlowService;
    private GraphJsonValidator graphJsonValidator;
    private FlowDesignPatchNormalizer patchNormalizer;
    private McpToolInvokeService service;

    @BeforeEach
    void setUp() {
        toolExecutor = mock(FlowDesignToolExecutor.class);
        contextFactory = mock(FlowDesignToolContextFactory.class);
        testProjectService = mock(ITestProjectService.class);
        testFlowService = mock(ITestFlowService.class);
        graphJsonValidator = mock(GraphJsonValidator.class);
        patchNormalizer = mock(FlowDesignPatchNormalizer.class);
        service = new McpToolInvokeService(
                toolExecutor, contextFactory, testProjectService, testFlowService,
                graphJsonValidator, patchNormalizer);
        when(testProjectService.selectTestProjectById(100L)).thenReturn(
                TestProject.builder().testProjectId(100L).mcpAutopilotEnabled(false).build());
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
     * 前提：项目未开 MCP 全自动；调用 submit_http_node。
     * 期望：抛 ServiceException（含开启提示）；不委托 Executor。
     */
    @Test
    @Order(2)
    @DisplayName("关开关时 submit 被拒绝")
    void invoke_submitUnit_rejectedWhenAutopilotOff() {
        McpToolInvokeParams params = new McpToolInvokeParams();
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.invoke(FlowDesignToolNames.SUBMIT_HTTP_NODE.getId(), params, 100L));
        assertTrue(ex.getMessage().contains("允许 MCP 全自动写流"));
        verify(toolExecutor, never()).executeTool(anyString(), any(), any(), anyBoolean());
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
                .build();
        when(contextFactory.fromMcpRequest(eq(params), eq(100L), isNull(), eq(false), isNull())).thenReturn(ctx);
        when(toolExecutor.executeTool(eq(FlowDesignToolExecutor.LIST_FLOWS), anyString(), eq(ctx), eq(true)))
                .thenReturn("{\"flows\":[]}");

        McpToolResult result = service.invoke(FlowDesignToolExecutor.LIST_FLOWS, params, 100L);
        assertEquals(FlowDesignToolExecutor.LIST_FLOWS, result.getTool());
        assertFalse(result.isError());
    }

    /**
     * 前提：项目已开 MCP 全自动写流。
     * 期望：查询开关为 true。
     */
    @Test
    @Order(4)
    @DisplayName("开开关时 isMcpAutopilotEnabled 为 true")
    void isMcpAutopilotEnabled_whenOn() {
        when(testProjectService.selectTestProjectById(100L)).thenReturn(
                TestProject.builder().testProjectId(100L).mcpAutopilotEnabled(true).build());
        assertTrue(service.isMcpAutopilotEnabled(100L));
    }

    /**
     * 前提：项目未开开关。
     * 期望：isMcpAutopilotEnabled 为 false。
     */
    @Test
    @Order(5)
    @DisplayName("关开关时 isMcpAutopilotEnabled 为 false")
    void isMcpAutopilotEnabled_whenOff() {
        assertFalse(service.isMcpAutopilotEnabled(100L));
    }

    /**
     * 前提：白名单工具 search_apis；Executor 返回含 error 的 JSON。
     * 期望：McpToolResult.error=true。
     */
    @Test
    @Order(6)
    @DisplayName("工具结果含 error 时标记失败")
    void invoke_errorResult_marksError() {
        McpToolInvokeParams params = new McpToolInvokeParams();
        FlowDesignToolContext ctx = FlowDesignToolContext.builder()
                .testProjectId(100L)
                .build();
        when(contextFactory.fromMcpRequest(eq(params), eq(100L), isNull(), eq(false), isNull())).thenReturn(ctx);
        when(toolExecutor.executeTool(eq(FlowDesignToolExecutor.SEARCH_APIS), anyString(), eq(ctx), eq(true)))
                .thenReturn("{\"error\":\"boom\"}");

        McpToolResult result = service.invoke(FlowDesignToolExecutor.SEARCH_APIS, params, 100L);
        assertTrue(result.isError());
    }

    /**
     * 前提：项目未开 MCP 全自动；调用 create_flow。
     * 期望：抛 ServiceException；不委托 Executor。
     */
    @Test
    @Order(7)
    @DisplayName("关开关时 create_flow 被拒绝")
    void invoke_createFlow_rejectedWhenAutopilotOff() {
        McpToolInvokeParams params = new McpToolInvokeParams();
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.invoke(FlowDesignToolNames.CREATE_FLOW.getId(), params, 100L));
        assertTrue(ex.getMessage().contains("允许 MCP 全自动写流"));
        verify(toolExecutor, never()).executeTool(anyString(), any(), any(), anyBoolean());
    }

    /**
     * 前提：项目未开 MCP 全自动；调用 update_flow_meta。
     * 期望：抛 ServiceException；不委托 Executor。
     */
    @Test
    @Order(8)
    @DisplayName("关开关时 update_flow_meta 被拒绝")
    void invoke_updateFlowMeta_rejectedWhenAutopilotOff() {
        McpToolInvokeParams params = new McpToolInvokeParams();
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.invoke(FlowDesignToolNames.UPDATE_FLOW_META.getId(), params, 100L));
        assertTrue(ex.getMessage().contains("允许 MCP 全自动写流"));
        verify(toolExecutor, never()).executeTool(anyString(), any(), any(), anyBoolean());
    }

    /**
     * 前提：项目已开 MCP 全自动；create_flow 不带 testFlowId。
     * 期望：可委托 Executor，不要求 testFlowId。
     */
    @Test
    @Order(9)
    @DisplayName("开开关时 create_flow 无需 testFlowId")
    void invoke_createFlow_allowedWithoutTestFlowId() {
        when(testProjectService.selectTestProjectById(100L)).thenReturn(
                TestProject.builder().testProjectId(100L).mcpAutopilotEnabled(true).build());
        McpToolInvokeParams params = new McpToolInvokeParams();
        params.setArguments(Map.of("flowName", "新流"));
        FlowDesignToolContext ctx = FlowDesignToolContext.builder()
                .testProjectId(100L)
                .operatorUserId(7L)
                .build();
        when(contextFactory.fromMcpRequest(eq(params), eq(100L), isNull(), eq(true), eq(7L))).thenReturn(ctx);
        when(toolExecutor.executeTool(eq(FlowDesignToolNames.CREATE_FLOW.getId()), anyString(), eq(ctx), eq(true)))
                .thenReturn("{\"testFlowId\":\"1\",\"flowName\":\"新流\"}");

        McpToolResult result = service.invoke(FlowDesignToolNames.CREATE_FLOW.getId(), params, 100L, 7L);
        assertEquals(FlowDesignToolNames.CREATE_FLOW.getId(), result.getTool());
        assertFalse(result.isError());
        verify(testFlowService, never()).selectTestFlowResult(any());
    }

    /**
     * 前提：项目未开「允许 MCP 导入接口」；调用 import_apis。
     * 期望：抛 ServiceException（含导入接口提示）；不委托 Executor。
     */
    @Test
    @Order(10)
    @DisplayName("关导入开关时 import_apis 被拒绝")
    void invoke_importApis_rejectedWhenImportOff() {
        McpToolInvokeParams params = new McpToolInvokeParams();
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.invoke(FlowDesignToolNames.IMPORT_APIS.getId(), params, 100L));
        assertTrue(ex.getMessage().contains("允许 MCP 导入接口"));
        verify(toolExecutor, never()).executeTool(anyString(), any(), any(), anyBoolean());
    }

    /**
     * 前提：仅开导入接口；import_apis 可调且不要求 testFlowId。
     * 期望：委托 Executor。
     */
    @Test
    @Order(11)
    @DisplayName("开导入开关时 import_apis 可调")
    void invoke_importApis_allowedWhenImportOn() {
        when(testProjectService.selectTestProjectById(100L)).thenReturn(
                TestProject.builder()
                        .testProjectId(100L)
                        .mcpAutopilotEnabled(false)
                        .mcpImportApisEnabled(true)
                        .build());
        McpToolInvokeParams params = new McpToolInvokeParams();
        params.setArguments(Map.of("items", List.of()));
        FlowDesignToolContext ctx = FlowDesignToolContext.builder()
                .testProjectId(100L)
                .operatorUserId(7L)
                .build();
        when(contextFactory.fromMcpRequest(eq(params), eq(100L), isNull(), eq(false), eq(7L))).thenReturn(ctx);
        when(toolExecutor.executeTool(eq(FlowDesignToolNames.IMPORT_APIS.getId()), anyString(), eq(ctx), eq(true)))
                .thenReturn("{\"ok\":true}");

        McpToolResult result = service.invoke(FlowDesignToolNames.IMPORT_APIS.getId(), params, 100L, 7L);
        assertEquals(FlowDesignToolNames.IMPORT_APIS.getId(), result.getTool());
        assertFalse(result.isError());
    }

    /**
     * 前提：项目已开 MCP 全自动；写工具未传 operatorUserId。
     * 期望：抛「Project Token 未绑定操作者」。
     */
    @Test
    @Order(12)
    @DisplayName("写工具缺操作者时明确报错")
    void invoke_writeTool_rejectsMissingOperator() {
        when(testProjectService.selectTestProjectById(100L)).thenReturn(
                TestProject.builder().testProjectId(100L).mcpAutopilotEnabled(true).build());
        McpToolInvokeParams params = new McpToolInvokeParams();
        params.setArguments(Map.of("flowName", "新流"));
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.invoke(FlowDesignToolNames.CREATE_FLOW.getId(), params, 100L, null));
        assertTrue(ex.getMessage().contains("未绑定操作者"));
        verify(toolExecutor, never()).executeTool(anyString(), any(), any(), anyBoolean());
    }
}
