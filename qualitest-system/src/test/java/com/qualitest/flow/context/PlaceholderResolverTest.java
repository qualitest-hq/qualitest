package com.qualitest.flow.context;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.flow.exception.FlowExecutionException;
import org.junit.jupiter.api.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link PlaceholderResolver} 单元测试：{{scope.path}} 占位符解析。
 * <p>
 * 覆盖 lenient（设计态，未定义 → 空串）与 strict（正式 Run，未定义 → 抛异常）两种模式；
 * env / flow / asset / http.* 快照路径、嵌套路径、混合文本。测试数据与前端
 * {@code placeholder-cases.json} 共享，保证 Java 与 TS 解析一致。
 * <p>
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=PlaceholderResolverTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PlaceholderResolverTest {

    /**
     * 由 fixture 中的 mockContext 构建，供各用例共用
     */
    private FlowRunContext ctx;
    /**
     * 设计态模式：未定义占位符 → 空串
     */
    private PlaceholderResolver lenient;
    /**
     * 正式 Run 模式：未定义占位符 → 抛 TF_PLACEHOLDER_UNDEFINED
     */
    private PlaceholderResolver strict;
    /**
     * fixture 中的 cases 数组，驱动数据驱动主测试
     */
    private JSONArray cases;

    /**
     * 每个 @Test 执行前重置解析器并加载 fixture。
     * <p>
     * 使用 @BeforeEach 而非 @BeforeAll，确保各测试方法之间状态隔离。
     */
    @BeforeEach
    void setUp() throws Exception {
        lenient = PlaceholderResolver.lenient();
        strict = PlaceholderResolver.strict();
        // classpath 根下的 /flow/placeholder-cases.json
        try (InputStream in = getClass().getResourceAsStream("/flow/placeholder-cases.json")) {
            assertNotNull(in, "fixture /flow/placeholder-cases.json");
            String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            JSONObject root = JSON.parseObject(json);
            ctx = buildContext(root.getJSONObject("mockContext"));
            cases = root.getJSONArray("cases");
        }
    }

    /**
     * 前提：fixture placeholder-cases.json 含 mockContext 与各 mode 用例。
     * 期望：lenient/strict 解析结果或 errorCode 与 fixture expected 一致。
     */
    @Test
    @Order(1)
    void fixtureCases_matchExpected() {
        assertNotNull(cases, "fixture cases 数组不应为空");
        for (int i = 0; i < cases.size(); i++) {
            JSONObject c = cases.getJSONObject(i);
            String id = c.getString("id");
            String template = c.getString("template");
            String mode = c.getString("mode");

            // lenient：未定义 → 空串，其余 case 与 strict 期望相同
            if ("both".equals(mode) || "lenient".equals(mode)) {
                String expected = c.getString("expected");
                String actual = lenient.resolve(template, ctx);
                assertEquals(expected, actual, "lenient case: " + id);
            }
            // strict：未定义 → 抛 FlowExecutionException；有 errorCode 则只校验错误码
            if ("both".equals(mode) || "strict".equals(mode)) {
                if (c.containsKey("errorCode")) {
                    FlowExecutionException ex = assertThrows(
                            FlowExecutionException.class,
                            () -> strict.resolve(template, ctx),
                            "strict case: " + id
                    );
                    assertEquals(c.getString("errorCode"), ex.getCode(), "case: " + id);
                } else {
                    String expected = c.getString("expected");
                    String actual = strict.resolve(template, ctx);
                    assertEquals(expected, actual, "strict case: " + id);
                }
            }
        }
    }

    /**
     * 前提：mockContext lastResponse.durationMs=120。
     * 期望：resolvePathSegment("http.duration") 返回 120L。
     */
    @Test
    @Order(2)
    void resolvePathSegment_httpDuration() {
        Object actual = lenient.resolvePathSegment(ctx, "http.duration");
        assertEquals(120L, actual, "http.duration 应取自 lastResponse.durationMs");
    }

    /**
     * 前提：mockContext lastResponse.body.data.code=0。
     * 期望：resolvePathSegment("http.body.data.code") 返回 0。
     */
    @Test
    @Order(3)
    void resolvePathSegment_httpBody() {
        Object actual = lenient.resolvePathSegment(ctx, "http.body.data.code");
        assertEquals(0, actual, "http.body.data.code 应从 lastResponse.body 按点路径取值");
    }

    /**
     * 前提：strict 模式解析未定义占位符 {{flow.missing}}。
     * 期望：抛 TF_PLACEHOLDER_UNDEFINED，placeholder 名为 flow.missing。
     */
    @Test
    @Order(4)
    void strict_throwsWithPlaceholderName() {
        FlowExecutionException ex = assertThrows(
                FlowExecutionException.class,
                () -> strict.resolve("{{flow.missing}}", ctx)
        );
        assertEquals("TF_PLACEHOLDER_UNDEFINED", ex.getCode(), "未定义占位符应抛该错误码");
        assertEquals("flow.missing", ex.getPlaceholder(), "异常应携带占位符名，便于定位");
    }

    /**
     * 将 fixture 中的 mockContext JSON 转为 {@link FlowRunContext}。
     * 结构与运行时 executor 注入的上下文一致：env / flow / asset / lastResponse（含 durationMs）。
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

    /**
     * JSONObject → Map，null 安全返回空 Map
     */
    private static Map<String, Object> toMap(JSONObject obj) {
        if (obj == null) {
            return new HashMap<>();
        }
        return new HashMap<>(obj);
    }
}
