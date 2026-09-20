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
                FlowDesignToolNames.IMPORT_APIS.getId(), false, false, true));
        assertFalse(FlowDesignToolNames.isMcpCallable(
                FlowDesignToolNames.SUBMIT_HTTP_NODE.getId(), false, false, true));
        assertTrue(FlowDesignToolNames.isMcpImportApisTool(
                FlowDesignToolNames.IMPORT_APIS.getId()));
        assertFalse(FlowDesignToolNames.isMcpAutoWriteTool(
                FlowDesignToolNames.IMPORT_APIS.getId()));
    }

    /**
     * 前提：仅开启自动写流（跑流关）。
     * 期望：submit / update_flow_meta 可调；run_test_flow / import_apis 不可调。
     */
    @Test
    @Order(2)
    @DisplayName("仅写流开：可 submit，不可 run_test_flow")
    void callable_autopilotOnly() {
        assertTrue(FlowDesignToolNames.isMcpCallable(
                FlowDesignToolNames.SUBMIT_HTTP_NODE.getId(), true, false, false));
        assertTrue(FlowDesignToolNames.isMcpCallable(
                FlowDesignToolNames.UPDATE_FLOW_META.getId(), true, false, false));
        assertTrue(FlowDesignToolNames.isMcpAutoWriteTool(
                FlowDesignToolNames.UPDATE_FLOW_META.getId()));
        assertFalse(FlowDesignToolNames.isMcpAutoWriteTool(
                FlowDesignToolNames.RUN_TEST_FLOW.getId()));
        assertFalse(FlowDesignToolNames.isMcpCallable(
                FlowDesignToolNames.RUN_TEST_FLOW.getId(), true, false, false));
        assertFalse(FlowDesignToolNames.isMcpCallable(
                FlowDesignToolNames.IMPORT_APIS.getId(), true, false, false));
        assertFalse(FlowDesignToolNames.isMcpCallable(
                FlowDesignToolNames.UPDATE_FLOW_META.getId(), false, false, false));
    }

    /**
     * 前提：写流与跑流都开。
     * 期望：run_test_flow 可调；isMcpAutorunTool 为 true。
     */
    @Test
    @Order(3)
    @DisplayName("写流+跑流开：可 run_test_flow")
    void callable_writeAndAutorun() {
        assertTrue(FlowDesignToolNames.isMcpAutorunTool(
                FlowDesignToolNames.RUN_TEST_FLOW.getId()));
        assertTrue(FlowDesignToolNames.isMcpCallable(
                FlowDesignToolNames.RUN_TEST_FLOW.getId(), true, true, false));
        assertFalse(FlowDesignToolNames.isMcpCallable(
                FlowDesignToolNames.RUN_TEST_FLOW.getId(), false, true, false));
    }

    /**
     * 前提：三开关都关。
     * 期望：只读 search_apis 可调；写工具与导入与跑流不可调。
     */
    @Test
    @Order(4)
    @DisplayName("三关：只读可调")
    void callable_bothOff_readonlyOk() {
        assertTrue(FlowDesignToolNames.isMcpCallable(
                FlowDesignToolNames.SEARCH_APIS.getId(), false, false, false));
        assertFalse(FlowDesignToolNames.isMcpCallable(
                FlowDesignToolNames.IMPORT_APIS.getId(), false, false, false));
        assertFalse(FlowDesignToolNames.isMcpCallable(
                FlowDesignToolNames.RUN_TEST_FLOW.getId(), false, false, false));
    }
}
