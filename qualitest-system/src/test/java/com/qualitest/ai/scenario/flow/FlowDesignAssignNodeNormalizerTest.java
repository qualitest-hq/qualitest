package com.qualitest.ai.scenario.flow;

import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 测 FlowDesignAssignNodeNormalizer：assignments 的 scope 归一（flow / out）与 op / name 归整。
 * 边界：纯 Map 变换，不落库、不跑图。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=FlowDesignAssignNodeNormalizerTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("AI assign 节点规范化")
class FlowDesignAssignNodeNormalizerTest {

    /**
     * 前提：一条赋值显式写 scope=out，另一条大小写混写。
     * 期望：均保留为 out（AI 可写定稿出参，供返回节点使用）。
     */
    @Test
    @Order(1)
    @DisplayName("scope=out 原样保留")
    void normalize_keepsOutScope() {
        Map<String, Object> data = assignments(List.of(
                row("status", "set", 200, "out"),
                row("body", "set", Map.of("ok", true), " OUT ")));

        FlowDesignAssignNodeNormalizer.normalize(data);

        List<JSONObject> rows = rowsOf(data);
        assertEquals("out", rows.get(0).getString("scope"));
        assertEquals("out", rows.get(1).getString("scope"));
    }

    /**
     * 前提：scope 缺省、空白、非法值 floww。
     * 期望：缺省补 flow；空白与非法原样保留，交给校验器报画布异常。
     */
    @Test
    @Order(2)
    @DisplayName("缺省补 flow，空白/非法原样保留")
    void normalize_unknownScope_keptForValidation() {
        Map<String, Object> data = assignments(List.of(
                row("a", "set", 1, null),
                row("b", "set", 2, "  "),
                row("c", "set", 3, "floww")));

        FlowDesignAssignNodeNormalizer.normalize(data);

        List<JSONObject> rows = rowsOf(data);
        assertEquals("flow", rows.get(0).getString("scope"));
        assertEquals("  ", rows.get(1).getString("scope"));
        assertEquals("floww", rows.get(2).getString("scope"));
    }

    /**
     * 前提：op 非法、name 两侧带空白。
     * 期望：op 归一为 set，name 去空白。
     */
    @Test
    @Order(3)
    @DisplayName("op 非法归一为 set 且 name 去空白")
    void normalize_normalizesOpAndName() {
        Map<String, Object> data = assignments(List.of(row("  out  ", "plus", 1, "out")));

        FlowDesignAssignNodeNormalizer.normalize(data);

        JSONObject row = rowsOf(data).get(0);
        assertEquals("out", row.getString("name"));
        assertEquals("set", row.getString("op"));
        assertEquals("out", row.getString("scope"));
    }

    /** 组装 data.assignments */
    private static Map<String, Object> assignments(List<Map<String, Object>> rows) {
        Map<String, Object> data = new HashMap<>();
        data.put("assignments", rows);
        return data;
    }

    /** 单条 assignment */
    private static Map<String, Object> row(String name, String op, Object value, String scope) {
        Map<String, Object> row = new HashMap<>();
        row.put("name", name);
        row.put("op", op);
        row.put("value", value);
        if (scope != null) {
            row.put("scope", scope);
        }
        return row;
    }

    /** 取出规范化后的 assignments 行 */
    @SuppressWarnings("unchecked")
    private static List<JSONObject> rowsOf(Map<String, Object> data) {
        return (List<JSONObject>) data.get("assignments");
    }
}
