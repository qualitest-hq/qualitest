package com.qualitest.flow.inspect;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * 测插件 ui.inspections 解析。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=PluginInspectionManifestSupportTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PluginInspectionManifestSupportTest {

    @Test
    @Order(1)
    @DisplayName("解析 ui.inspections 并拼 pluginId/code")
    void parseUiInspections() {
        // 前提：说明书 ui.inspections 含 platform 与 invoke 两项
        // 期望：生效码带 pluginId 前缀，evaluator 为枚举
        Map<String, Object> ui = Map.of(
                "inspections",
                List.of(
                        Map.of(
                                "code", "BIND_ALIAS_MISSING",
                                "name", "未配置 bindAlias",
                                "category", "项目挂载",
                                "defaultSeverity", "ERROR",
                                "evaluator", "platform.projectMount.required"),
                        Map.of(
                                "code", "CUSTOM",
                                "defaultSeverity", "WARNING",
                                "evaluator", "invoke",
                                "capability", "inspect.flow")));
        List<FlowInspectionDef> defs =
                PluginInspectionManifestSupport.parseFromUi("qualitest.demo", ui);
        assertEquals(2, defs.size());
        assertEquals("qualitest.demo/BIND_ALIAS_MISSING", defs.get(0).getCode());
        assertEquals(InspectionEvaluator.PROJECT_MOUNT_REQUIRED, defs.get(0).getEvaluator());
        assertEquals("qualitest.demo/CUSTOM", defs.get(1).getCode());
        assertEquals(InspectionConfiguredSeverity.WARNING, defs.get(1).getDefaultSeverity());
        assertFalse(defs.get(1).isLocked());
        assertEquals("inspect.flow", defs.get(1).getCapability());
        assertEquals(InspectionEvaluator.INVOKE, defs.get(1).getEvaluator());
    }

    @Test
    @Order(2)
    @DisplayName("未知 evaluator 或 invoke 缺 capability 跳过")
    void skipInvalid() {
        // 前提：未知求值方式；invoke 未写 capability
        // 期望：都不进目录
        Map<String, Object> ui = Map.of(
                "inspections",
                List.of(
                        Map.of("code", "X", "evaluator", "platform.unknown"),
                        Map.of("code", "Y", "evaluator", "invoke")));
        assertEquals(0, PluginInspectionManifestSupport.parseFromUi("p", ui).size());
    }
}
