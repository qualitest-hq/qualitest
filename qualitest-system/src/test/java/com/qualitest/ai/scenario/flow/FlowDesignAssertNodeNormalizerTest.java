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
 * 测 FlowDesignAssertNodeNormalizer：断言节点规则归一化（运算符别名、去 mustache）。
 * 边界：纯函数，改写 data Map。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=FlowDesignAssertNodeNormalizerTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlowDesignAssertNodeNormalizerTest {

    /**
     * 前提：规则 left 带 {{}}，operator 为 equals。
     * 期望：left 去掉 mustache；operator 变为 eq。
     */
    @Test
    @Order(1)
    @DisplayName("equals 转 eq 并剥掉 mustache")
    void normalize_equalsToEq_andStripMustache() {
        Map<String, Object> data = new HashMap<>();
        data.put("rules", List.of(Map.of(
                "left", "{{flow.mobile}}",
                "operator", "equals",
                "right", "13800000001"
        )));

        FlowDesignAssertNodeNormalizer.normalize(data);

        @SuppressWarnings("unchecked")
        List<JSONObject> rules = (List<JSONObject>) (List<?>) data.get("rules");
        JSONObject rule = rules.get(0);
        assertEquals("flow.mobile", rule.getString("left"));
        assertEquals("eq", rule.getString("operator"));
        assertEquals("13800000001", rule.getString("right"));
    }

    /**
     * 前提：传入 EQUALS / == / != / gt 等别名。
     * 期望：统一为 eq / ne / gt。
     */
    @Test
    @Order(2)
    @DisplayName("运算符别名统一为标准码")
    void normalizeOperator_aliases() {
        assertEquals("eq", FlowDesignAssertNodeNormalizer.normalizeOperator("EQUALS"));
        assertEquals("eq", FlowDesignAssertNodeNormalizer.normalizeOperator("=="));
        assertEquals("ne", FlowDesignAssertNodeNormalizer.normalizeOperator("!="));
        assertEquals("gt", FlowDesignAssertNodeNormalizer.normalizeOperator("gt"));
    }

    /**
     * 前提：完整 {{flow.x}}、带后缀字符串、非字符串。
     * 期望：仅整段 mustache 被剥掉；其它原样返回。
     */
    @Test
    @Order(3)
    @DisplayName("仅整段 mustache 被剥掉")
    void stripMustache_onlyFullWrap() {
        assertEquals("flow.x", FlowDesignAssertNodeNormalizer.stripMustache("{{flow.x}}"));
        assertEquals("{{flow.x}} suffix", FlowDesignAssertNodeNormalizer.stripMustache("{{flow.x}} suffix"));
        assertEquals(12, FlowDesignAssertNodeNormalizer.stripMustache(12));
    }
}
