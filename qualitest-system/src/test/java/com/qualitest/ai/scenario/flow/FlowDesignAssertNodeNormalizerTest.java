package com.qualitest.ai.scenario.flow;

import com.alibaba.fastjson2.JSONObject;
import com.qualitest.flow.context.CompareRuleEvaluator;
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
 * 测断言规则规范化：运算符别名、去 mustache、{@code $} 左值改 http.body、condition branches。
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
        assertEquals("exists", FlowDesignAssertNodeNormalizer.normalizeOperator("notempty"));
        assertEquals("exists", FlowDesignAssertNodeNormalizer.normalizeOperator("not_empty"));
    }

    /**
     * 前提：完整 {{flow.x}}、带后缀字符串、非字符串。
     * 期望：仅整段 mustache 被剥掉；其它原样返回。
     */
    @Test
    @Order(3)
    @DisplayName("仅整段 mustache 被剥掉")
    void stripMustache_onlyFullWrap() {
        assertEquals("flow.x", CompareRuleEvaluator.stripMustache("{{flow.x}}"));
        assertEquals("{{flow.x}} suffix", CompareRuleEvaluator.stripMustache("{{flow.x}} suffix"));
        assertEquals(12, CompareRuleEvaluator.stripMustache(12));
    }

    /**
     * 前提：左值为 $.data.code，operator 为 notempty。
     * 期望：left → http.body.data.code；operator → exists。
     */
    @Test
    @Order(4)
    @DisplayName("$ 左值与 notempty 规范化")
    void normalize_dollarLeft_andNotempty() {
        Map<String, Object> data = new HashMap<>();
        data.put("rules", List.of(Map.of(
                "left", "$.data[?(@.cartId==5001)]",
                "operator", "notempty",
                "right", ""
        )));

        FlowDesignAssertNodeNormalizer.normalize(data);

        @SuppressWarnings("unchecked")
        List<JSONObject> rules = (List<JSONObject>) (List<?>) data.get("rules");
        JSONObject rule = rules.get(0);
        assertEquals("http.body.data[?(@.cartId==5001)]", rule.getString("left"));
        assertEquals("exists", rule.getString("operator"));
    }

    /**
     * 前提：condition branches 含 $ 左值。
     * 期望：conditions 同步规范化。
     */
    @Test
    @Order(5)
    @DisplayName("condition branches conditions 规范化")
    void normalizeConditionBranches_dollarLeft() {
        Map<String, Object> data = new HashMap<>();
        Map<String, Object> branch = new HashMap<>();
        branch.put("id", "if-1");
        branch.put("kind", "if");
        branch.put("conditions", List.of(Map.of(
                "left", "$.data.code",
                "operator", "equals",
                "right", "0"
        )));
        data.put("branches", List.of(branch));

        FlowDesignAssertNodeNormalizer.normalizeConditionBranches(data);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> branches = (List<Map<String, Object>>) data.get("branches");
        @SuppressWarnings("unchecked")
        List<JSONObject> conditions = (List<JSONObject>) (List<?>) branches.get(0).get("conditions");
        assertEquals("http.body.data.code", conditions.get(0).getString("left"));
        assertEquals("eq", conditions.get(0).getString("operator"));
    }
}
