package com.qualitest.ai.mcp;

import com.qualitest.ai.mcp.protocol.McpSession;
import com.qualitest.ai.scenario.flow.FlowDesignPatchNormalizer;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.FlowDesignToolContextFactory;
import com.qualitest.ai.tools.FlowDesignToolExecutor;
import com.qualitest.ai.tools.FlowDesignToolNames;
import com.qualitest.api.params.McpToolInvokeParams;
import com.qualitest.api.result.McpToolResult;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.validate.GraphJsonValidator;
import com.qualitest.project.domain.TestProject;
import com.qualitest.project.result.TestFlowResult;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 测 MCP 工具调用门控与执行编排。
 * 覆盖：按角色权限推导的只读/写流/跑流/导入门控、操作者校验、错误回执标记。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=McpToolInvokeServiceTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class McpToolInvokeServiceTest {

    private static final Long PROJECT_ID = 100L;
    private static final Long OPERATOR_ID = 7L;

    private FlowDesignToolExecutor toolExecutor;
    private FlowDesignToolContextFactory contextFactory;
    private ITestProjectService testProjectService;
    private McpProjectGateResolver mcpProjectGateResolver;
    private ITestFlowService testFlowService;
    private GraphJsonValidator graphJsonValidator;
    private FlowDesignPatchNormalizer patchNormalizer;
    private McpToolInvokeService service;

    @BeforeEach
    void setUp() {
        toolExecutor = mock(FlowDesignToolExecutor.class);
        contextFactory = mock(FlowDesignToolContextFactory.class);
        testProjectService = mock(ITestProjectService.class);
        mcpProjectGateResolver = mock(McpProjectGateResolver.class);
        testFlowService = mock(ITestFlowService.class);
        graphJsonValidator = mock(GraphJsonValidator.class);
        patchNormalizer = mock(FlowDesignPatchNormalizer.class);
        service = new McpToolInvokeService(
                toolExecutor, contextFactory, testProjectService,
                mcpProjectGateResolver,
                testFlowService, graphJsonValidator, patchNormalizer);
        when(testProjectService.selectTestProjectById(PROJECT_ID)).thenReturn(
                TestProject.builder().testProjectId(PROJECT_ID).build());
        stubGates(true, false, false, false);
    }

    /** stub MCP 四道门控 */
    private void stubGates(boolean read, boolean write, boolean run, boolean importApis) {
        when(mcpProjectGateResolver.resolve(eq(PROJECT_ID), eq(OPERATOR_ID)))
                .thenReturn(new McpProjectGates(read, write, run, importApis));
    }

    /**
     * 前提：工具名 unknown（非白名单）；操作者仅有只读。
     * 期望：抛 ServiceException。
     */
    @Test
    @Order(1)
    @DisplayName("未知工具名抛 ServiceException")
    void invoke_unknownTool_throws() {
        McpToolInvokeParams params = new McpToolInvokeParams();
        assertThrows(ServiceException.class, () -> service.invoke("unknown", params, PROJECT_ID, OPERATOR_ID));
    }

    /**
     * 前提：角色无写流权限；调用 submit_http_node。
     * 期望：抛 ServiceException（含写流提示）；不委托 Executor。
     */
    @Test
    @Order(2)
    @DisplayName("无写流权限时 submit 被拒绝")
    void invoke_submitUnit_rejectedWhenWriteOff() {
        McpToolInvokeParams params = new McpToolInvokeParams();
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.invoke(FlowDesignToolNames.SUBMIT_HTTP_NODE.getId(), params, PROJECT_ID, OPERATOR_ID));
        assertTrue(ex.getMessage().contains("MCP 写流") || ex.getMessage().contains("写流"));
        verify(toolExecutor, never()).executeTool(anyString(), any(), any(), anyBoolean());
    }

    /**
     * 前提：角色有只读；白名单工具 list_flows。
     * 期望：经 ContextFactory 后调 Executor；result.tool=list_flows。
     */
    @Test
    @Order(3)
    @DisplayName("list_flows 白名单通过并委托")
    void invoke_listFlows_allowed() {
        McpToolInvokeParams params = new McpToolInvokeParams();
        FlowDesignToolContext ctx = FlowDesignToolContext.builder()
                .testProjectId(PROJECT_ID)
                .build();
        when(contextFactory.fromMcpRequest(eq(params), eq(PROJECT_ID), isNull(), eq(false), eq(OPERATOR_ID))).thenReturn(ctx);
        when(toolExecutor.executeTool(eq(FlowDesignToolExecutor.LIST_FLOWS), anyString(), eq(ctx), eq(true)))
                .thenReturn("{\"flows\":[]}");

        McpToolResult result = service.invoke(FlowDesignToolExecutor.LIST_FLOWS, params, PROJECT_ID, OPERATOR_ID);
        assertEquals(FlowDesignToolExecutor.LIST_FLOWS, result.getTool());
        assertFalse(result.isError());
    }

    /**
     * 前提：角色具备写流权限。
     * 期望：resolveMcpGates.autoWriteEnabled 为 true。
     */
    @Test
    @Order(4)
    @DisplayName("有写流权限时 autoWriteEnabled 为 true")
    void resolveMcpGates_writeOn() {
        stubGates(true, true, false, false);
        assertTrue(service.resolveMcpGates(PROJECT_ID, OPERATOR_ID).autoWriteEnabled());
    }

    /**
     * 前提：角色仅只读。
     * 期望：autoWriteEnabled 为 false。
     */
    @Test
    @Order(5)
    @DisplayName("仅只读时 autoWriteEnabled 为 false")
    void resolveMcpGates_writeOff() {
        assertFalse(service.resolveMcpGates(PROJECT_ID, OPERATOR_ID).autoWriteEnabled());
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
                .testProjectId(PROJECT_ID)
                .build();
        when(contextFactory.fromMcpRequest(eq(params), eq(PROJECT_ID), isNull(), eq(false), eq(OPERATOR_ID))).thenReturn(ctx);
        when(toolExecutor.executeTool(eq(FlowDesignToolExecutor.SEARCH_APIS), anyString(), eq(ctx), eq(true)))
                .thenReturn("{\"error\":\"boom\"}");

        McpToolResult result = service.invoke(FlowDesignToolExecutor.SEARCH_APIS, params, PROJECT_ID, OPERATOR_ID);
        assertTrue(result.isError());
    }

    /**
     * 前提：角色无写流；调用 create_flow。
     * 期望：抛 ServiceException；不委托 Executor。
     */
    @Test
    @Order(7)
    @DisplayName("无写流权限时 create_flow 被拒绝")
    void invoke_createFlow_rejectedWhenWriteOff() {
        McpToolInvokeParams params = new McpToolInvokeParams();
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.invoke(FlowDesignToolNames.CREATE_FLOW.getId(), params, PROJECT_ID, OPERATOR_ID));
        assertTrue(ex.getMessage().contains("写流"));
        verify(toolExecutor, never()).executeTool(anyString(), any(), any(), anyBoolean());
    }

    /**
     * 前提：角色无写流；调用 update_flow_meta。
     * 期望：抛 ServiceException；不委托 Executor。
     */
    @Test
    @Order(8)
    @DisplayName("无写流权限时 update_flow_meta 被拒绝")
    void invoke_updateFlowMeta_rejectedWhenWriteOff() {
        McpToolInvokeParams params = new McpToolInvokeParams();
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.invoke(FlowDesignToolNames.UPDATE_FLOW_META.getId(), params, PROJECT_ID, OPERATOR_ID));
        assertTrue(ex.getMessage().contains("写流"));
        verify(toolExecutor, never()).executeTool(anyString(), any(), any(), anyBoolean());
    }

    /**
     * 前提：角色有写流；create_flow 不带 testFlowId。
     * 期望：可委托 Executor，不要求 testFlowId。
     */
    @Test
    @Order(9)
    @DisplayName("有写流权限时 create_flow 无需 testFlowId")
    void invoke_createFlow_allowedWithoutTestFlowId() {
        stubGates(true, true, false, false);
        McpToolInvokeParams params = new McpToolInvokeParams();
        params.setArguments(Map.of("flowName", "新流"));
        FlowDesignToolContext ctx = FlowDesignToolContext.builder()
                .testProjectId(PROJECT_ID)
                .operatorUserId(OPERATOR_ID)
                .build();
        when(contextFactory.fromMcpRequest(eq(params), eq(PROJECT_ID), isNull(), eq(true), eq(OPERATOR_ID))).thenReturn(ctx);
        when(toolExecutor.executeTool(eq(FlowDesignToolNames.CREATE_FLOW.getId()), anyString(), eq(ctx), eq(true)))
                .thenReturn("{\"testFlowId\":\"1\",\"flowName\":\"新流\"}");

        McpToolResult result = service.invoke(FlowDesignToolNames.CREATE_FLOW.getId(), params, PROJECT_ID, OPERATOR_ID);
        assertEquals(FlowDesignToolNames.CREATE_FLOW.getId(), result.getTool());
        assertFalse(result.isError());
        verify(testFlowService, never()).selectTestFlowResult(any());
    }

    /**
     * 前提：角色无导入权限；调用 import_apis。
     * 期望：抛 ServiceException（含导入提示）；不委托 Executor。
     */
    @Test
    @Order(10)
    @DisplayName("无导入权限时 import_apis 被拒绝")
    void invoke_importApis_rejectedWhenImportOff() {
        McpToolInvokeParams params = new McpToolInvokeParams();
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.invoke(FlowDesignToolNames.IMPORT_APIS.getId(), params, PROJECT_ID, OPERATOR_ID));
        assertTrue(ex.getMessage().contains("导入"));
        verify(toolExecutor, never()).executeTool(anyString(), any(), any(), anyBoolean());
    }

    /**
     * 前提：仅有导入权限；import_apis 可调且不要求 testFlowId。
     * 期望：委托 Executor。
     */
    @Test
    @Order(11)
    @DisplayName("有导入权限时 import_apis 可调")
    void invoke_importApis_allowedWhenImportOn() {
        stubGates(false, false, false, true);
        McpToolInvokeParams params = new McpToolInvokeParams();
        params.setArguments(Map.of("items", List.of()));
        FlowDesignToolContext ctx = FlowDesignToolContext.builder()
                .testProjectId(PROJECT_ID)
                .operatorUserId(OPERATOR_ID)
                .build();
        when(contextFactory.fromMcpRequest(eq(params), eq(PROJECT_ID), isNull(), eq(false), eq(OPERATOR_ID))).thenReturn(ctx);
        when(toolExecutor.executeTool(eq(FlowDesignToolNames.IMPORT_APIS.getId()), anyString(), eq(ctx), eq(true)))
                .thenReturn("{\"ok\":true}");

        McpToolResult result = service.invoke(FlowDesignToolNames.IMPORT_APIS.getId(), params, PROJECT_ID, OPERATOR_ID);
        assertEquals(FlowDesignToolNames.IMPORT_APIS.getId(), result.getTool());
        assertFalse(result.isError());
    }

    /**
     * 前提：有写流、无跑流；调用 run_test_flow。
     * 期望：抛 ServiceException（含跑流提示）；不委托 Executor。
     */
    @Test
    @Order(13)
    @DisplayName("有写流无跑流时 run_test_flow 被拒绝")
    void invoke_runTestFlow_rejectedWhenRunOff() {
        stubGates(true, true, false, false);
        McpToolInvokeParams params = new McpToolInvokeParams();
        params.setTestFlowId(1L);
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.invoke(FlowDesignToolNames.RUN_TEST_FLOW.getId(), params, PROJECT_ID, OPERATOR_ID));
        assertTrue(ex.getMessage().contains("跑流"));
        verify(toolExecutor, never()).executeTool(anyString(), any(), any(), anyBoolean());
    }

    /**
     * 前提：有跑流、无写流；调用 run_test_flow。
     * 期望：拒绝文案提示须具备写流；不委托 Executor。
     */
    @Test
    @Order(15)
    @DisplayName("有跑流无写流时 run_test_flow 提示须写流")
    void invoke_runTestFlow_rejectedWhenWriteOffEvenIfRunOn() {
        stubGates(true, false, true, false);
        McpToolInvokeParams params = new McpToolInvokeParams();
        params.setTestFlowId(1L);
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.invoke(FlowDesignToolNames.RUN_TEST_FLOW.getId(), params, PROJECT_ID, OPERATOR_ID));
        assertTrue(ex.getMessage().contains("写流"));
        verify(toolExecutor, never()).executeTool(anyString(), any(), any(), anyBoolean());
    }

    /**
     * 前提：角色具备跑流权限。
     * 期望：autorunEnabled 为 true。
     */
    @Test
    @Order(14)
    @DisplayName("有跑流权限时 autorunEnabled 为 true")
    void resolveMcpGates_runOn() {
        stubGates(true, true, true, false);
        assertTrue(service.resolveMcpGates(PROJECT_ID, OPERATOR_ID).autorunEnabled());
    }

    /**
     * 前提：回合会话绑定测试流 9，参数未带 testFlowId；角色有写流与跑流。
     * 期望：两次 submit 复用同一累积器，执行器 mcpMode=false，回执含 received 且不含 committed，工作图留在会话上。
     */
    @Test
    @Order(16)
    @DisplayName("回合会话 submit 回 received 并保留工作图")
    void invoke_turn_submitKeepsGraphWithoutCommit() {
        stubGates(true, true, true, false);
        when(testFlowService.selectTestFlowResult(9L)).thenReturn(
                TestFlowResult.builder()
                        .testFlowId(9L)
                        .testProjectId(PROJECT_ID)
                        .graphJson("{}")
                        .graphRevision(1L)
                        .build());
        GraphJson graph = GraphJson.builder().build();
        FlowDesignToolContext ctx = FlowDesignToolContext.builder()
                .testProjectId(PROJECT_ID)
                .testFlowId(9L)
                .graphJson(graph)
                .build();
        McpSession session = new McpSession("s1", PROJECT_ID, McpSession.Mode.TURN, 9L);
        when(contextFactory.fromMcpRequest(any(), eq(PROJECT_ID), eq(session.getCapture()), eq(true), eq(OPERATOR_ID)))
                .thenReturn(ctx);
        when(toolExecutor.executeTool(eq("submit_http_node"), anyString(), eq(ctx), eq(false)))
                .thenReturn("{\"received\":true}");

        McpToolInvokeParams first = new McpToolInvokeParams();
        McpToolResult result = service.invoke("submit_http_node", first, PROJECT_ID, OPERATOR_ID, session);
        assertEquals(9L, first.getTestFlowId());
        assertFalse(result.isError());
        assertTrue(result.getResultJson().contains("received"));
        assertFalse(result.getResultJson().contains("committed"));
        assertSame(graph, session.getGraph());

        McpToolInvokeParams second = new McpToolInvokeParams();
        service.invoke("submit_http_node", second, PROJECT_ID, OPERATOR_ID, session);
        assertSame(graph, second.getGraphJson());
        verify(toolExecutor, times(2)).executeTool(eq("submit_http_node"), anyString(), eq(ctx), eq(false));
    }

    /**
     * 前提：回合会话调用 list_flows。
     * 期望：拒绝，不委托执行器。
     */
    @Test
    @Order(17)
    @DisplayName("回合会话不提供列流")
    void invoke_turn_rejectsListFlows() {
        McpSession session = new McpSession("s1", PROJECT_ID, McpSession.Mode.TURN, 9L);
        ServiceException ex = assertThrows(ServiceException.class,
                () -> service.invoke("list_flows", new McpToolInvokeParams(), PROJECT_ID, OPERATOR_ID, session));
        assertTrue(ex.getMessage().contains("回合会话不提供"));
        verify(toolExecutor, never()).executeTool(anyString(), any(), any(), anyBoolean());
    }
}
