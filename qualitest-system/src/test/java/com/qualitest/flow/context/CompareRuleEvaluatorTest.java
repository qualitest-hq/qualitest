package com.qualitest.flow.context;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 测 CompareRuleEvaluator：断言/条件分支比较规则求值。
 * 边界：fixture compare-extract-cases.json；无 DB。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=CompareRuleEvaluatorTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class CompareRuleEvaluatorTest {

    /**
     * 夹具 mockContext 转成的运行时上下文，各用例只读共享
     */
    private FlowRunContext ctx;
    /**
     * compareCases 数组
     */
    private JSONArray compareCases;

    @BeforeEach
    void setUp() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/flow/compare-extract-cases.json")) {
            assert in != null;
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            JSONObject root = JSON.parseObject(json);
            ctx = buildContext(root.getJSONObject("mockContext"));
            compareCases = root.getJSONArray("compareCases");
        }
    }

    /**
     * 前提：fixture compareCases 含 rule 与 mockContext 上下文。
     * 期望：每条 case 的 eval 布尔结果与 expected 一致。
     */
    @Test
    @Order(1)
    @DisplayName("求值：fixture 各 compareCases 与 expected 一致")
    void eval_fixtureCases() {
        for (int i = 0; i < compareCases.size(); i++) {
            JSONObject c = compareCases.getJSONObject(i);
            String id = c.getString("id");
            boolean actual = CompareRuleEvaluator.eval(c.getJSONObject("rule"), ctx);
            boolean expected = c.getBooleanValue("expected");
            assertEquals(expected, actual, "case: " + id);
        }
    }

    /**
     * 前提：左值为 JSON 金额 Double(195.0)，右值为整型字面量 195。
     * 期望：eq 按数值相等通过（避免 String.valueOf(195.0)="195.0" 与 "195" 不相等）。
     */
    @Test
    @Order(2)
    @DisplayName("eq：Double 金额与整型右值数值相等")
    void eq_doubleAmountEqualsIntegerRight() {
        FlowRunContext amountCtx = FlowRunContext.builder()
                .lastResponse(FlowRunContext.HttpResponseSnapshot.builder()
                        .status(200)
                        .body(JSON.parseObject("{\"data\":{\"payAmount\":195.0}}"))
                        .build())
                .build();
        JSONObject rule = JSON.parseObject(
                "{\"left\":\"http.body.data.payAmount\",\"operator\":\"eq\",\"right\":195}");
        assertEquals(true, CompareRuleEvaluator.eval(rule, amountCtx));
    }

    /**
     * 前提：支付后余额 805.0，flow.balanceBefore=1000；右值裸写 flow.balanceBefore。
     * 期望：lt 通过（右值按作用域路径解析，而非字面量字符串）。
     */
    @Test
    @Order(3)
    @DisplayName("lt：右值裸写 flow.xxx 按路径解析")
    void lt_bareFlowPathOnRight() {
        Map<String, Object> flow = new HashMap<>();
        flow.put("balanceBefore", 1000.0);
        FlowRunContext amountCtx = FlowRunContext.builder()
                .flow(flow)
                .lastResponse(FlowRunContext.HttpResponseSnapshot.builder()
                        .status(200)
                        .body(JSON.parseObject("{\"data\":{\"balance\":805.0}}"))
                        .build())
                .build();
        JSONObject rule = JSON.parseObject(
                "{\"left\":\"http.body.data.balance\",\"operator\":\"lt\",\"right\":\"flow.balanceBefore\"}");
        assertEquals(true, CompareRuleEvaluator.eval(rule, amountCtx));
    }

    /**
     * 将夹具 JSON 中的 mockContext 转为 {@link FlowRunContext}
     */
    private static FlowRunContext buildContext(JSONObject mock) {
        FlowRunContext.FlowRunContextBuilder builder = FlowRunContext.builder()
                .env(toMap(mock.getJSONObject("env")))
                .flow(toMap(mock.getJSONObject("flow")))
                .asset(toMap(mock.getJSONObject("asset")));

        JSONObject last = mock.getJSONObject("lastResponse");
        if (last != null) {
            FlowRunContext.HttpResponseSnapshot.HttpResponseSnapshotBuilder snap =
                    FlowRunContext.HttpResponseSnapshot.builder()
                            .status(last.getIntValue("status"))
                            .headers(last.getJSONObject("headers") == null
                                    ? new HashMap<>()
                                    : toMap(last.getJSONObject("headers")))
                            .body(last.get("body"));
            if (last.containsKey("durationMs")) {
                snap.durationMs(last.getLongValue("durationMs"));
            }
            builder.lastResponse(snap.build());
        }
        return builder.build();
    }

    private static Map<String, Object> toMap(JSONObject obj) {
        if (obj == null) {
            return new HashMap<>();
        }
        return new HashMap<>(obj);
    }
}
