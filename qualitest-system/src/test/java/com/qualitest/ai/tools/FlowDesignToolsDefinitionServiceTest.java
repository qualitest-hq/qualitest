package com.qualitest.ai.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * 测工具定义加载：Web 半自动/全自动清单、MCP 只读清单、MCP 自动写追加写工具。
 */
@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlowDesignToolsDefinitionServiceTest {

    @Mock
    private FlowDesignToolExecutor flowDesignToolExecutor;

    private FlowDesignToolsDefinitionService service;

    @BeforeEach
    void setUp() {
        when(flowDesignToolExecutor.registeredToolNames()).thenReturn(FlowDesignToolExecutor.allDeclaredToolNames());
        service = new FlowDesignToolsDefinitionService(flowDesignToolExecutor);
        service.validateToolRegistryConsistency();
    }

    /**
     * 前提：执行器已注册全部声明工具；加载 Web JSON 清单。
     * 期望：数量与 webAgent 枚举一致；含 submit、upsert、run；不含 list_flows、get_flow。
     */
    @Test
    @Order(1)
    @DisplayName("Web JSON 含 submit/upsert/run，不含 list_flows")
    void loadToolsDefinition_containsSubmit_notListFlows() {
        List<String> names = service.loadToolsDefinition().stream()
                .map(tool -> {
                    Object fn = tool.get("function");
                    if (fn instanceof java.util.Map<?, ?> fnMap) {
                        return String.valueOf(fnMap.get("name"));
                    }
                    return null;
                })
                .toList();

        assertEquals(FlowDesignToolNames.webAgentToolIds().size(), names.size());
        assertTrue(names.contains(FlowDesignToolNames.SUBMIT_HTTP_NODE.getId()));
        assertTrue(names.contains(FlowDesignToolNames.GET_EDGE_DETAIL.getId()));
        assertTrue(names.contains(FlowDesignToolNames.GET_SCENARIO_DETAIL.getId()));
        assertTrue(names.contains(FlowDesignToolNames.UPSERT_ASSET_VARIABLES.getId()));
        assertTrue(names.contains(FlowDesignToolNames.APPEND_API_DESIGN_HINTS.getId()));
        assertTrue(names.contains(FlowDesignToolNames.UPDATE_FLOW_META.getId()));
        assertTrue(names.contains(FlowDesignToolNames.GET_FLOW_API_HEALTH.getId()));
        assertTrue(names.contains(FlowDesignToolNames.LIST_ASSET_VARIABLES.getId()));
        assertTrue(names.contains(FlowDesignToolNames.RUN_TEST_FLOW.getId()));
        assertFalse(names.contains(FlowDesignToolNames.LIST_FLOWS.getId()));
        assertFalse(names.contains(FlowDesignToolNames.GET_FLOW.getId()));
        assertFalse(names.contains(FlowDesignToolNames.CREATE_FLOW.getId()));
        assertFalse(names.contains("commit_design_patch"));
    }

    /**
     * 前提：加载 Web 工具且 autopilotEnabled=false。
     * 期望：不含 run。
     */
    @Test
    @Order(2)
    @DisplayName("半自动时剔除 run")
    void loadToolsDefinition_withoutAutopilot_excludesRun() {
        List<String> names = service.loadToolsDefinition(false).stream()
                .map(tool -> {
                    Object fn = tool.get("function");
                    if (fn instanceof java.util.Map<?, ?> fnMap) {
                        return String.valueOf(fnMap.get("name"));
                    }
                    return null;
                })
                .toList();
        assertFalse(names.contains(FlowDesignToolNames.RUN_TEST_FLOW.getId()));
        assertTrue(names.contains(FlowDesignToolNames.SUBMIT_HTTP_NODE.getId()));
    }

    /**
     * 前提：加载 Web 工具且 autopilotEnabled=true。
     * 期望：含 run。
     */
    @Test
    @Order(3)
    @DisplayName("全自动注入 run")
    void loadToolsDefinition_withAutopilot_includesRun() {
        List<String> names = service.loadToolsDefinition(true).stream()
                .map(tool -> {
                    Object fn = tool.get("function");
                    if (fn instanceof java.util.Map<?, ?> fnMap) {
                        return String.valueOf(fnMap.get("name"));
                    }
                    return null;
                })
                .toList();
        assertTrue(names.contains(FlowDesignToolNames.RUN_TEST_FLOW.getId()));
    }

    /**
     * 前提：执行器已注册全部声明工具；加载 MCP 工具定义。
     * 期望：含 list_flows、get_flow、get_edge_detail、get_scenario_detail；不含任何 submit_*、upsert、append、run。
     */
    @Test
    @Order(4)
    @DisplayName("MCP 只读工具不含 submit/upsert/run")
    void loadMcpProtocolTools_excludesSubmit() {
        List<String> names = service.loadMcpProtocolTools().stream()
                .map(tool -> String.valueOf(tool.get("name")))
                .toList();

        assertEquals(FlowDesignToolNames.mcpAllowedToolIds().size(), names.size());
        assertFalse(names.stream().anyMatch(FlowDesignToolNames::isSubmitUnitTool));
        assertFalse(names.contains(FlowDesignToolNames.UPSERT_ASSET_VARIABLES.getId()));
        assertFalse(names.contains(FlowDesignToolNames.APPEND_API_DESIGN_HINTS.getId()));
        assertFalse(names.contains(FlowDesignToolNames.RUN_TEST_FLOW.getId()));
        assertFalse(names.contains("commit_design_patch"));
        assertTrue(names.contains(FlowDesignToolNames.LIST_FLOWS.getId()));
        assertTrue(names.contains(FlowDesignToolNames.GET_FLOW.getId()));
        assertTrue(names.contains(FlowDesignToolNames.GET_MCP_GUIDE_VERSION.getId()));
        assertTrue(names.contains(FlowDesignToolNames.GET_EDGE_DETAIL.getId()));
        assertTrue(names.contains(FlowDesignToolNames.GET_SCENARIO_DETAIL.getId()));
        assertTrue(names.contains(FlowDesignToolNames.GET_FLOW_API_HEALTH.getId()));
        assertTrue(names.contains(FlowDesignToolNames.LIST_ASSET_VARIABLES.getId()));
    }

    /**
     * 前提：已加载 MCP 工具定义。
     * 期望：名称集合等于枚举中 mcpAllowed=true 的全部 id。
     */
    @Test
    @Order(5)
    @DisplayName("MCP 工具 id 等于枚举 mcpAllowed 集合")
    void mcpToolIds_matchEnum() {
        Set<String> mcpNames = service.loadMcpProtocolTools().stream()
                .map(tool -> String.valueOf(tool.get("name")))
                .collect(Collectors.toSet());

        assertEquals(FlowDesignToolNames.mcpAllowedToolIds(), mcpNames);
    }

    /**
     * 前提：开启 MCP 自动写流、未开跑流时的工具列表。
     * 期望：含只读工具与写工具，不含 run_test_flow；名称无重复。
     */
    @Test
    @Order(6)
    @DisplayName("MCP 写流列表含写工具不含跑流")
    void loadMcpProtocolTools_autopilot_includesWriteTools() {
        List<String> names = service.loadMcpProtocolTools(true, false, false).stream()
                .map(tool -> String.valueOf(tool.get("name")))
                .toList();
        Set<String> unique = Set.copyOf(names);
        assertEquals(names.size(), unique.size());
        assertEquals(FlowDesignToolNames.mcpAutoWriteEnabledToolIds(), unique);
        assertTrue(names.contains(FlowDesignToolNames.SUBMIT_HTTP_NODE.getId()));
        assertFalse(names.contains(FlowDesignToolNames.RUN_TEST_FLOW.getId()));
        assertTrue(names.contains(FlowDesignToolNames.UPSERT_ASSET_VARIABLES.getId()));
        assertTrue(names.contains(FlowDesignToolNames.CREATE_FLOW.getId()));
        assertTrue(names.contains(FlowDesignToolNames.UPDATE_FLOW_META.getId()));
        assertTrue(names.contains(FlowDesignToolNames.LIST_FLOWS.getId()));
    }

    /**
     * 前提：写流与跑流都开。
     * 期望：列表含 run_test_flow；写流开时含 upsert_project_env。
     */
    @Test
    @Order(61)
    @DisplayName("MCP 写流+跑流列表含 run_test_flow；写流含 upsert_project_env")
    void loadMcpProtocolTools_autorun_includesRun() {
        List<String> names = service.loadMcpProtocolTools(true, true, false).stream()
                .map(tool -> String.valueOf(tool.get("name")))
                .toList();
        assertTrue(names.contains(FlowDesignToolNames.RUN_TEST_FLOW.getId()));
        assertTrue(names.contains(FlowDesignToolNames.UPSERT_PROJECT_ENV.getId()));
        assertTrue(names.contains(FlowDesignToolNames.SUBMIT_HTTP_NODE.getId()));
    }

    /**
     * 前提：未开全自动时的 MCP 只读列表。
     * 期望：不含 create_flow / update_flow_meta。
     */
    @Test
    @Order(7)
    @DisplayName("只读 MCP 列表不含 create_flow / update_flow_meta")
    void loadMcpProtocolTools_readonly_excludesCreateFlow() {
        List<String> names = service.loadMcpProtocolTools().stream()
                .map(tool -> String.valueOf(tool.get("name")))
                .toList();
        assertFalse(names.contains(FlowDesignToolNames.CREATE_FLOW.getId()));
        assertFalse(names.contains(FlowDesignToolNames.UPDATE_FLOW_META.getId()));
        assertTrue(names.contains(FlowDesignToolNames.LIST_FLOWS.getId()));
    }

    /**
     * 前提：MCP 跑流列表中的 run_test_flow。
     * 期望：inputSchema 必填 testFlowId；描述写明无画布当前流与自动跑流开关。
     */
    @Test
    @Order(8)
    @DisplayName("MCP run_test_flow 合同必填 testFlowId")
    @SuppressWarnings("unchecked")
    void loadMcpProtocolTools_runTestFlow_requiresTestFlowId() {
        Map<String, Object> runTool = service.loadMcpProtocolTools(true, true, false).stream()
                .filter(t -> FlowDesignToolNames.RUN_TEST_FLOW.getId().equals(String.valueOf(t.get("name"))))
                .findFirst()
                .orElseThrow();
        String desc = String.valueOf(runTool.get("description"));
        assertFalse(desc.contains("触发当前测试流"));
        assertTrue(desc.contains("testFlowId"));
        assertTrue(desc.contains("无画布"));
        assertTrue(desc.contains("允许 MCP 自动跑流"));
        Map<String, Object> schema = (Map<String, Object>) runTool.get("inputSchema");
        List<String> required = (List<String>) schema.get("required");
        assertTrue(required.contains("testFlowId"));
    }

    /**
     * 前提：MCP 只读列表中的 get_run_failure。
     * 期望：inputSchema 必填 runId。
     */
    @Test
    @Order(9)
    @DisplayName("MCP get_run_failure 合同必填 runId")
    @SuppressWarnings("unchecked")
    void loadMcpProtocolTools_getRunFailure_requiresRunId() {
        Map<String, Object> tool = service.loadMcpProtocolTools().stream()
                .filter(t -> FlowDesignToolNames.GET_RUN_FAILURE.getId().equals(String.valueOf(t.get("name"))))
                .findFirst()
                .orElseThrow();
        Map<String, Object> schema = (Map<String, Object>) tool.get("inputSchema");
        List<String> required = (List<String>) schema.get("required");
        assertTrue(required.contains("runId"));
    }
}
