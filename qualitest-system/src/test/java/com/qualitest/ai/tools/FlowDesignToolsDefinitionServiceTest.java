package com.qualitest.ai.tools;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
 * FlowDesignToolsDefinitionService 单元测试。
 * <p>
 * 覆盖：Web 工具列表含 submit 与 get_flow_api_health、不含 list_flows/get_flow；
 * MCP 列表共 12 个只读工具、含 get_flow_api_health、不含 submit。
 */
@ExtendWith(MockitoExtension.class)
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

    /** Web 列表应有 11 个工具：含 submit、get_flow_api_health；不含仅 MCP 的 list_flows/get_flow */
    @Test
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
        assertEquals(11, names.size());
        assertTrue(names.contains(FlowDesignToolNames.SUBMIT_FLOW_DESIGN_PATCH.getId()));
        assertTrue(names.contains(FlowDesignToolNames.GET_FLOW_API_HEALTH.getId()));
        assertFalse(names.contains(FlowDesignToolNames.LIST_FLOWS.getId()));
        assertFalse(names.contains(FlowDesignToolNames.GET_FLOW.getId()));
    }

    /** MCP 列表应有 12 个只读工具：含 get_flow_api_health、list_flows、get_flow；不含 submit */
    @Test
    void loadMcpProtocolTools_hasTwelveTools_excludesSubmit() {
        List<String> names = service.loadMcpProtocolTools().stream()
                .map(tool -> String.valueOf(tool.get("name")))
                .toList();
        assertEquals(12, names.size());
        assertFalse(names.contains(FlowDesignToolNames.SUBMIT_FLOW_DESIGN_PATCH.getId()));
        assertTrue(names.contains(FlowDesignToolNames.LIST_FLOWS.getId()));
        assertTrue(names.contains(FlowDesignToolNames.GET_FLOW.getId()));
        assertTrue(names.contains(FlowDesignToolNames.GET_FLOW_API_HEALTH.getId()));
    }

    @Test
    void mcpToolIds_matchEnum() {
        Set<String> mcpNames = service.loadMcpProtocolTools().stream()
                .map(tool -> String.valueOf(tool.get("name")))
                .collect(Collectors.toSet());
        assertEquals(FlowDesignToolNames.mcpAllowedToolIds(), mcpNames);
    }
}
