package com.qualitest.flow.context;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.qualitest.flow.support.FlowTestSections.begin;
import static com.qualitest.flow.support.FlowTestSections.end;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link ExtractApplicator} 单元测试：验证 HTTP 响应变量提取逻辑。
 * <p>
 * 被测对象根据 extracts 配置（from=body/header/status、expr、scope、name），
 * 从上一步 HTTP 响应快照中提取值，写入 env / flow / asset 对应作用域，
 * 并返回 applied 列表供 stepDetails 记录。
 * <p>
 * 数据驱动：用例来自 {@code classpath:flow/compare-extract-cases.json} 的 extractCases 数组，
 * 覆盖 JsonPath body 提取、header 提取、status 码、asset 作用域、regex 未实现返回 null 等场景。
 * <p>
 * 运行（qualitest 目录）：mvn test -pl qualitest-system -am -DskipTests=false -Dtest=ExtractApplicatorTest
 */
class ExtractApplicatorTest {

    /**
     * 原始 mockContext JSON，每条用例重新 buildContext 避免状态污染
     */
    private JSONObject mockContextJson;
    /**
     * 各用例共用的 HTTP 响应快照
     */
    private FlowRunContext.HttpResponseSnapshot response;
    private JSONArray extractCases;

    @BeforeEach
    void setUp() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/flow/compare-extract-cases.json")) {
            assert in != null;
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            JSONObject root = JSON.parseObject(json);
            mockContextJson = root.getJSONObject("mockContext");
            FlowRunContext base = buildContext(mockContextJson);
            response = base.getLastResponse();
            extractCases = root.getJSONArray("extractCases");
        }
    }

    /**
     * 数据驱动主测试：遍历 extractCases，对每条 extracts 配置调用 {@link ExtractApplicator#apply}。
     * 断言 applied 条目的 name/scope/value，以及可选的 flowAfter / envAfter / assetAfter 副作用。
     * 每条用例独立 buildContext，避免状态污染。
     */
    @Test
    void apply_fixtureCases() {
        begin("apply_fixtureCases");
        for (int i = 0; i < extractCases.size(); i++) {
            JSONObject c = extractCases.getJSONObject(i);
            String id = c.getString("id");
            FlowRunContext ctx = buildContext(mockContextJson);

            List<JSONObject> applied = ExtractApplicator.apply(c.getJSONArray("extracts"), ctx, response);
            JSONArray expected = c.getJSONArray("expected");
            System.out.printf("  OK %-22s  applied=%s%n", id, JSON.toJSONString(applied));

            // 本步报告条数与字段
            assertEquals(expected.size(), applied.size(), id);
            for (int j = 0; j < expected.size(); j++) {
                JSONObject exp = expected.getJSONObject(j);
                JSONObject act = applied.get(j);
                assertEquals(exp.getString("name"), act.getString("name"), id + ".name");
                assertEquals(exp.getString("scope"), act.getString("scope"), id + ".scope");
                assertEquals(exp.get("value"), act.get("value"), id + ".value");
            }

            // flow / env / asset 写入结果
            assertScopeAfter(c, ctx, id);
        }
        end("apply_fixtureCases");
    }

    /**
     * 校验夹具中可选的 flowAfter、envAfter、assetAfter
     */
    private static void assertScopeAfter(JSONObject c, FlowRunContext ctx, String id) {
        JSONObject flowAfter = c.getJSONObject("flowAfter");
        if (flowAfter != null) {
            for (String key : flowAfter.keySet()) {
                assertEquals(flowAfter.get(key), ctx.getFlow().get(key), id + ".flow." + key);
            }
        }
        JSONObject envAfter = c.getJSONObject("envAfter");
        if (envAfter != null) {
            for (String key : envAfter.keySet()) {
                assertEquals(envAfter.get(key), ctx.getEnv().get(key), id + ".env." + key);
            }
        }
        JSONObject assetAfter = c.getJSONObject("assetAfter");
        if (assetAfter != null) {
            assertEquals(assetAfter, ctx.getAsset(), id + ".asset");
        }
    }

    /**
     * 将夹具 JSON 中的 mockContext 转为 {@link FlowRunContext}
     */
    @SuppressWarnings("unchecked")
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
