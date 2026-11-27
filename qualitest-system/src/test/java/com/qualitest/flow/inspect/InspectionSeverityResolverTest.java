package com.qualitest.flow.inspect;

import com.qualitest.flow.validate.FlowIssue;
import com.qualitest.flow.validate.FlowIssueCodes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测 InspectionSeverityResolver：locked、覆盖、OFF 过滤、解析地板码。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=InspectionSeverityResolverTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class InspectionSeverityResolverTest {

    private final InspectionSeverityResolver resolver = new InspectionSeverityResolver();

    @Test
    @Order(1)
    @DisplayName("locked 码忽略覆盖，固定 ERROR")
    void lockedIgnoresOverride() {
        // 前提：合成 locked 目录项；profile 试图 OFF
        // 期望：仍为 ERROR
        FlowInspectionDef def = FlowInspectionDef.builder()
                .code("LOCKED_SAMPLE")
                .shortCode("LOCKED_SAMPLE")
                .name("锁定样例")
                .category("测试")
                .defaultSeverity(InspectionConfiguredSeverity.ERROR)
                .locked(true)
                .build();
        assertEquals(
                InspectionConfiguredSeverity.ERROR,
                resolver.resolve(
                        def.getCode(),
                        def,
                        Map.of(def.getCode(), InspectionConfiguredSeverity.OFF)));
    }

    @Test
    @Order(2)
    @DisplayName("applyProfile：OFF 丢弃，WARNING 保留")
    void applyProfileFiltersOff() {
        // 前提：两条 issue，profile 把 ORPHAN_PARAM 保持默认 WARNING，API_MISSING 设 OFF
        // 期望：只剩 WARNING 一条
        Map<String, FlowInspectionDef> defs = Map.of(
                FlowIssueCodes.API_MISSING,
                BuiltinFlowInspections.ALL.stream()
                        .filter(d -> FlowIssueCodes.API_MISSING.equals(d.getCode()))
                        .findFirst()
                        .orElseThrow(),
                FlowIssueCodes.ORPHAN_PARAM,
                BuiltinFlowInspections.ALL.stream()
                        .filter(d -> FlowIssueCodes.ORPHAN_PARAM.equals(d.getCode()))
                        .findFirst()
                        .orElseThrow());
        List<FlowIssue> raw = List.of(
                FlowIssue.error(FlowIssueCodes.API_MISSING, FlowIssue.Stage.CANVAS, "n1", "缺 API"),
                FlowIssue.error(FlowIssueCodes.ORPHAN_PARAM, FlowIssue.Stage.CANVAS, "n1", "孤儿参"));
        List<FlowIssue> out = resolver.applyProfile(
                raw,
                defs,
                InspectionProfileSupport.toJson(Map.of(
                        FlowIssueCodes.API_MISSING, InspectionConfiguredSeverity.OFF)));
        assertEquals(1, out.size());
        assertTrue(out.get(0).isWarning());
        assertEquals(FlowIssueCodes.ORPHAN_PARAM, out.get(0).getCode());
    }

    @Test
    @Order(3)
    @DisplayName("GRAPH_JSON_INVALID 忽略覆盖，固定 ERROR")
    void graphJsonInvalidIgnoresOverride() {
        // 前提：解析地板码不在目录；profile 试图 OFF
        // 期望：仍为 ERROR（不能被流检查关掉）
        assertEquals(
                InspectionConfiguredSeverity.ERROR,
                resolver.resolve(
                        FlowIssueCodes.GRAPH_JSON_INVALID,
                        null,
                        Map.of(FlowIssueCodes.GRAPH_JSON_INVALID, InspectionConfiguredSeverity.OFF)));
    }

    @Test
    @Order(4)
    @DisplayName("未知插件检查码无目录时默认 WARNING")
    void unknownCodeDefaultsToWarning() {
        // 前提：无目录定义、无 profile 覆盖
        // 期望：WARNING，避免漏登说明书意外硬拦开跑
        assertEquals(
                InspectionConfiguredSeverity.WARNING,
                resolver.resolve("some.plugin/CUSTOM_CHECK", null, Map.of()));
    }
}
