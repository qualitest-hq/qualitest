package com.qualitest.ai.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * 测 FlowDesignToolsDefinitionService：Web / MCP 工具清单差异。
 * 边界：Mock FlowDesignToolExecutor.registeredToolNames，不启真实 Agent。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=FlowDesignToolsDefinitionServiceTest
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
     * 前提：registeredToolNames 为全部声明工具。
     * 期望：Web 列表含 submit、get_flow_api_health；不含 list_flows / get_flow。
     */
    @Test
    @Order(1)
    @DisplayName("Web 清单含 submit 不含 list_flows")
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
        assertEquals(12, names.size());
        assertTrue(names.contains(FlowDesignToolNames.SUBMIT_FLOW_DESIGN_PATCH.getId()));
        assertTrue(names.contains(FlowDesignToolNames.GET_FLOW_API_HEALTH.getId()));
        assertTrue(names.contains(FlowDesignToolNames.LIST_ASSET_VARIABLES.getId()));
        assertFalse(names.contains(FlowDesignToolNames.LIST_FLOWS.getId()));
        assertFalse(names.contains(FlowDesignToolNames.GET_FLOW.getId()));
    }

    /**
     * 前提：registeredToolNames 为全部声明工具。
     * 期望：MCP 列表 13 个、含 list_flows/get_flow/get_flow_api_health/list_asset_variables，不含 submit。
     */
    @Test
    @Order(2)
    @DisplayName("MCP 十三工具不含 submit")
    void loadMcpProtocolTools_hasThirteenTools_excludesSubmit() {
        List<String> names = service.loadMcpProtocolTools().stream()
                .map(tool -> String.valueOf(tool.get("name")))
                .toList();
        assertEquals(13, names.size());
        assertFalse(names.contains(FlowDesignToolNames.SUBMIT_FLOW_DESIGN_PATCH.getId()));
        assertTrue(names.contains(FlowDesignToolNames.LIST_FLOWS.getId()));
        assertTrue(names.contains(FlowDesignToolNames.GET_FLOW.getId()));
        assertTrue(names.contains(FlowDesignToolNames.GET_FLOW_API_HEALTH.getId()));
        assertTrue(names.contains(FlowDesignToolNames.LIST_ASSET_VARIABLES.getId()));
    }

    /**
     * 前提：加载 MCP 工具列表。
     * 期望：名称集合与 FlowDesignToolNames.mcpAllowedToolIds() 一致。
     */
    @Test
    @Order(3)
    @DisplayName("MCP 工具 id 与枚举一致")
    void mcpToolIds_matchEnum() {
        Set<String> mcpNames = service.loadMcpProtocolTools().stream()
                .map(tool -> String.valueOf(tool.get("name")))
                .collect(Collectors.toSet());
        assertEquals(FlowDesignToolNames.mcpAllowedToolIds(), mcpNames);
    }
}
