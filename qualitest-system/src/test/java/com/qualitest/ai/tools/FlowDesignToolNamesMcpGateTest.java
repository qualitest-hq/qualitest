package com.qualitest.ai.tools;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测 FlowDesignToolNames：写流开关与导入接口开关各自控制不同工具。
 * 纯函数，无数据库。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=FlowDesignToolNamesMcpGateTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlowDesignToolNamesMcpGateTest {

    /**
     * 前提：仅开启导入接口。
     * 期望：import_apis 可调；submit_http_node 不可调。
     */
    @Test
    @Order(1)
    @DisplayName("仅导入开：可 import_apis，不可 submit")
    void callable_importOnly() {
        assertTrue(FlowDesignToolNames.isMcpCallable(
                FlowDesignToolNames.IMPORT_APIS.getId(), false, true));
        assertFalse(FlowDesignToolNames.isMcpCallable(
                FlowDesignToolNames.SUBMIT_HTTP_NODE.getId(), false, true));
        assertTrue(FlowDesignToolNames.isMcpImportApisTool(
                FlowDesignToolNames.IMPORT_APIS.getId()));
        assertFalse(FlowDesignToolNames.isMcpAutopilotWriteTool(
                FlowDesignToolNames.IMPORT_APIS.getId()));
    }

    /**
     * 前提：仅开启全自动写流。
     * 期望：submit / update_flow_meta 可调；import_apis 不可调。
     */
    @Test
    @Order(2)
    @DisplayName("仅写流开：可 submit 与 update_flow_meta，不可 import_apis")
    void callable_autopilotOnly() {
        assertTrue(FlowDesignToolNames.isMcpCallable(
                FlowDesignToolNames.SUBMIT_HTTP_NODE.getId(), true, false));
        assertTrue(FlowDesignToolNames.isMcpCallable(
                FlowDesignToolNames.UPDATE_FLOW_META.getId(), true, false));
        assertTrue(FlowDesignToolNames.isMcpAutopilotWriteTool(
                FlowDesignToolNames.UPDATE_FLOW_META.getId()));
        assertFalse(FlowDesignToolNames.isMcpCallable(
                FlowDesignToolNames.IMPORT_APIS.getId(), true, false));
        assertFalse(FlowDesignToolNames.isMcpCallable(
                FlowDesignToolNames.UPDATE_FLOW_META.getId(), false, false));
    }

    /**
     * 前提：两开关都关。
     * 期望：只读 search_apis 可调；写工具与导入不可调。
     */
    @Test
    @Order(3)
    @DisplayName("双关：只读可调")
    void callable_bothOff_readonlyOk() {
        assertTrue(FlowDesignToolNames.isMcpCallable(
                FlowDesignToolNames.SEARCH_APIS.getId(), false, false));
        assertFalse(FlowDesignToolNames.isMcpCallable(
                FlowDesignToolNames.IMPORT_APIS.getId(), false, false));
    }
}
