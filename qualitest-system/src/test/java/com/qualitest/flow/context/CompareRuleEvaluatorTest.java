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
