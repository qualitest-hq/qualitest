package com.qualitest.flow.context;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static com.qualitest.flow.support.FlowTestSections.begin;
import static com.qualitest.flow.support.FlowTestSections.end;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link CompareRuleEvaluator} 单元测试：验证断言/条件分支中的比较规则求值。
 * <p>
 * 被测对象读取 rule 的 left（如 flow.code、http.body.data.token、asset.xxx）、
 * operator（eq/ne/gt/lt/gte/lte/contains/exists 等 9 种）、right（支持占位符），
 * 在 {@link FlowRunContext} 上求值并返回 boolean。
 * <p>
 * 数据驱动：用例来自 {@code classpath:flow/compare-extract-cases.json} 的 compareCases 数组。
 * <p>
 * 运行（qualitest 目录）：mvn test -pl qualitest-system -am -DskipTests=false -Dtest=CompareRuleEvaluatorTest
 */
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
     * 数据驱动主测试：遍历 compareCases，对每条 rule 调用 {@link CompareRuleEvaluator#eval}，
     * 断言返回值与 fixture 中的 expected 一致。失败时断言消息含用例 id 便于定位。
     */
    @Test
    void eval_fixtureCases() {
        begin("eval_fixtureCases");
        for (int i = 0; i < compareCases.size(); i++) {
            JSONObject c = compareCases.getJSONObject(i);
            String id = c.getString("id");
            boolean actual = CompareRuleEvaluator.eval(c.getJSONObject("rule"), ctx);
            boolean expected = c.getBooleanValue("expected");
            System.out.printf("  OK %-22s  ->  %s (expected %s)%n", id, actual, expected);
            assertEquals(expected, actual, id);
        }
        end("eval_fixtureCases");
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
