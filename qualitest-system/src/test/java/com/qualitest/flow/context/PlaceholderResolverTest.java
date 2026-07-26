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

import static com.qualitest.flow.support.FlowTestSections.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link PlaceholderResolver} 单元测试：验证 {@code {{scope.path}}} 占位符的解析行为。
 * <p>
 * 两种模式：lenient（设计态，未定义占位符 → 空串）与 strict（正式 Run，未定义 → 抛异常）。
 * 支持 env / flow / asset / http.* 快照路径、嵌套路径、混合文本。
 * <p>
 * 测试数据与前端 {@code qualitest-ui/.../placeholder-cases.json} 共享，保证 Java 与 TS 解析一致。
 * <p>
 * 运行（qualitest 目录）：mvn test -pl qualitest-system -am -DskipTests=false -Dtest=PlaceholderResolverTest
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
     * 数据驱动主测试：遍历 fixture 中全部 case，按 mode 分别断言 lenient / strict 行为。
     * <p>
     * fixture 字段说明：
     * <ul>
     *   <li>{@code id} — 用例标识，失败时出现在断言消息中</li>
     *   <li>{@code template} — 待解析模板，含 {@code {{scope.path}}} 占位符</li>
     *   <li>{@code expected} — lenient 或 strict 成功时的期望结果</li>
     *   <li>{@code errorCode} — strict 模式下期望抛出的错误码（与 expected 互斥）</li>
     *   <li>{@code mode} — {@code both} | {@code lenient} | {@code strict}，控制本 case 跑哪些模式</li>
     * </ul>
     * 覆盖范围：env / flow / asset / http.* 快照、嵌套路径、混合文本、null 模板、未定义占位符。
     */
    @Test
    @Order(1)
    void fixtureCases_matchExpected() {
        begin("fixtureCases_matchExpected");
        assertNotNull(cases);
        System.out.println("  cases: " + cases.size());
        for (int i = 0; i < cases.size(); i++) {
            JSONObject c = cases.getJSONObject(i);
            String id = c.getString("id");
            String template = c.getString("template");
            String mode = c.getString("mode");

            // lenient：未定义 → 空串，其余 case 与 strict 期望相同
            if ("both".equals(mode) || "lenient".equals(mode)) {
                String expected = c.getString("expected");
                String actual = lenient.resolve(template, ctx);
                logCase("lenient", id, template, actual);
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
                    logCase("strict", id, template, "throw " + ex.getCode());
                    assertEquals(c.getString("errorCode"), ex.getCode(), "case: " + id);
                } else {
                    String expected = c.getString("expected");
                    String actual = strict.resolve(template, ctx);
                    logCase("strict", id, template, actual);
                    assertEquals(expected, actual, "strict case: " + id);
                }
            }
        }
        end("fixtureCases_matchExpected");
    }

    /**
     * {@code http.duration}：上一步 HTTP 耗时（毫秒）。
     * fixture mockContext.lastResponse.durationMs = 120。
     */
    @Test
    @Order(2)
    void resolvePathSegment_httpDuration() {
        begin("resolvePathSegment_httpDuration");
        Object actual = lenient.resolvePathSegment(ctx, "http.duration");
        System.out.println("  resolvePathSegment(\"http.duration\") -> " + actual);
        assertEquals(120L, actual);
        end("resolvePathSegment_httpDuration");
    }

    /**
     * {@code http.body.*}：从上一步响应 Body 按点路径取值。
     * fixture lastResponse.body.data.code = 0。
     */
    @Test
    @Order(3)
    void resolvePathSegment_httpBody() {
        begin("resolvePathSegment_httpBody");
        Object actual = lenient.resolvePathSegment(ctx, "http.body.data.code");
        System.out.println("  resolvePathSegment(\"http.body.data.code\") -> " + actual);
        assertEquals(0, actual);
        end("resolvePathSegment_httpBody");
    }

    /**
     * strict 模式下，异常应携带占位符名 {@code flow.missing}，便于前端/日志定位。
     */
    @Test
    @Order(4)
    void strict_throwsWithPlaceholderName() {
        begin("strict_throwsWithPlaceholderName");
        FlowExecutionException ex = assertThrows(
                FlowExecutionException.class,
                () -> strict.resolve("{{flow.missing}}", ctx)
        );
        System.out.println("  strict \"{{flow.missing}}\" -> throw " + ex.getCode()
                + ", placeholder=" + ex.getPlaceholder());
        assertEquals("TF_PLACEHOLDER_UNDEFINED", ex.getCode());
        assertEquals("flow.missing", ex.getPlaceholder());
        end("strict_throwsWithPlaceholderName");
    }

    private static void logCase(String resolveMode, String id, String template, Object result) {
        System.out.printf("  OK [%s] %-24s  %s  ->  %s%n",
                resolveMode, id, quote(template), result);
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
