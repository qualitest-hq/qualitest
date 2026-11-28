package com.qualitest.flow.http;

import com.qualitest.api.params.DebugHttpForwardParams;
import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.project.domain.TestProjectApi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测发出装箱契约：同结构行 → Cookie / path / query 形态与调试台规则一致。
 * 边界：内存 API/节点；不测工作台测值与流测值相等；无网络。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=IssuedHttpWireParityTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class IssuedHttpWireParityTest {

    /**
     * 前提：资产 cookies 与节点 cookies 各有一行。
     * 期望：合成单条 Cookie 头（name=value; …），不出现以 cookie 名为名的独立头。
     */
    @Test
    @Order(1)
    @DisplayName("Cookie 行合成单条 Cookie 头")
    void build_packsCookiesIntoCookieHeader() {
        TestProjectApi api = TestProjectApi.builder()
                .testProjectApiId(1L)
                .testProjectId(1L)
                .apiPath("/api/me")
                .requestConfig("""
                        {"configVersion":1,"method":"GET","queryParams":[],"pathParams":[],
                         "declaredHeaders":[],"body":{"mode":"none"}}
                        """)
                .cookies("{\"sid\":\"from-asset\"}")
                .authConfig("{\"mode\":\"none\"}")
                .build();

        Map<String, Object> nodeData = new HashMap<>();
        nodeData.put("callMode", "project");
        nodeData.put("testProjectApiId", "1");
        nodeData.put("cookies", List.of(
                Map.of("name", "token", "value", "abc", "_enabled", true)
        ));

        FlowHttpRequestBuilder.BuiltHttpRequest built = FlowHttpRequestBuilder.buildFromProject(
                FlowRunContext.builder().env(Map.of("baseUrl", "http://localhost:8801")).build(),
                api,
                nodeData);

        Map<String, String> headers = headerMap(built);
        assertTrue(headers.containsKey("Cookie") || headers.keySet().stream()
                .anyMatch(k -> k != null && k.equalsIgnoreCase("Cookie")));
        String cookie = headers.entrySet().stream()
                .filter(e -> e.getKey() != null && e.getKey().equalsIgnoreCase("Cookie"))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse("");
        assertTrue(cookie.contains("sid=from-asset"));
        assertTrue(cookie.contains("token=abc"));
        assertFalse(headers.containsKey("sid"));
        assertFalse(headers.containsKey("token"));
    }

    /**
     * 前提：path 参数值为含空格字符串。
     * 期望：URL path 使用 %20，不出现 +。
     */
    @Test
    @Order(2)
    @DisplayName("path 空格编码为 %20")
    void build_encodesPathSpaceAsPercent20() {
        TestProjectApi api = TestProjectApi.builder()
                .testProjectApiId(2L)
                .testProjectId(1L)
                .apiPath("/files/{name}")
                .requestConfig("""
                        {
                          "configVersion":1,
                          "method":"GET",
                          "queryParams":[],
                          "pathParams":[{"name":"name","value":"a b","_enabled":true}],
                          "declaredHeaders":[],
                          "body":{"mode":"none"}
                        }
                        """)
                .authConfig("{\"mode\":\"none\"}")
                .build();

        Map<String, Object> nodeData = new HashMap<>();
        nodeData.put("callMode", "project");
        nodeData.put("testProjectApiId", "2");

        FlowHttpRequestBuilder.BuiltHttpRequest built = FlowHttpRequestBuilder.buildFromProject(
                FlowRunContext.builder().env(Map.of("baseUrl", "http://localhost:8801")).build(),
                api,
                nodeData);

        assertTrue(built.getUrl().contains("/files/a%20b"));
        assertFalse(built.getUrl().contains("/files/a+b"));
    }

    /**
     * 前提：query 含有值与空值行。
     * 期望：仅发出有值参数（与调试台跳过空值一致）。
     */
    @Test
    @Order(3)
    @DisplayName("空 query 不发出")
    void build_skipsBlankQueryLikeDebug() {
        TestProjectApi api = TestProjectApi.builder()
                .testProjectApiId(3L)
                .testProjectId(1L)
                .apiPath("/list")
                .requestConfig("""
                        {
                          "configVersion":1,
                          "method":"GET",
                          "queryParams":[
                            {"name":"q","value":"hello world","_enabled":true},
                            {"name":"empty","value":"","_enabled":true}
                          ],
                          "pathParams":[],
                          "declaredHeaders":[],
                          "body":{"mode":"none"}
                        }
                        """)
                .authConfig("{\"mode\":\"none\"}")
                .build();

        Map<String, Object> nodeData = new HashMap<>();
        nodeData.put("callMode", "project");
        nodeData.put("testProjectApiId", "3");

        FlowHttpRequestBuilder.BuiltHttpRequest built = FlowHttpRequestBuilder.buildFromProject(
                FlowRunContext.builder().env(Map.of("baseUrl", "http://localhost:8801")).build(),
                api,
                nodeData);

        assertTrue(built.getUrl().contains("q=hello%20world"));
        assertFalse(built.getUrl().contains("empty="));
    }

    /**
     * 前提：paramDefaults 含结构中不存在的 extra，且禁用行 page。
     * 期望：issued 后 page 启用有值、extra 追加。
     */
    @Test
    @Order(4)
    @DisplayName("叠层：paramDefaults 追加并重开禁用行")
    void overlay_appendsAndReenablesParamDefaults() {
        TestProjectApi api = TestProjectApi.builder()
                .testProjectApiId(4L)
                .testProjectId(1L)
                .apiPath("/search")
                .requestConfig("""
                        {
                          "configVersion":1,
                          "method":"GET",
                          "queryParams":[
                            {"name":"page","value":"","_enabled":false}
                          ],
                          "pathParams":[],
                          "declaredHeaders":[],
                          "body":{"mode":"none"}
                        }
                        """)
                .authConfig("{\"mode\":\"none\"}")
                .build();

        Map<String, Object> nodeData = new HashMap<>();
        nodeData.put("callMode", "project");
        nodeData.put("testProjectApiId", "4");
        nodeData.put("requestValueOverrides", Map.of(
                "paramDefaults", Map.of("page", "2", "extra", "x")
        ));

        FlowHttpRequestBuilder.BuiltHttpRequest built = FlowHttpRequestBuilder.buildFromProject(
                FlowRunContext.builder().env(Map.of("baseUrl", "http://localhost:8801")).build(),
                api,
                nodeData);

        assertTrue(built.getUrl().contains("page=2"));
        assertTrue(built.getUrl().contains("extra=x"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> headerMap(FlowHttpRequestBuilder.BuiltHttpRequest built) {
        assertNotNull(built.getForwardParams());
        List<DebugHttpForwardParams.HeaderPair> pairs = built.getForwardParams().getHeaders();
        Map<String, String> out = new HashMap<>();
        if (pairs == null) {
            return out;
        }
        for (DebugHttpForwardParams.HeaderPair p : pairs) {
            if (p != null && p.getName() != null) {
                out.put(p.getName(), p.getValue());
            }
        }
        return out;
    }
}
