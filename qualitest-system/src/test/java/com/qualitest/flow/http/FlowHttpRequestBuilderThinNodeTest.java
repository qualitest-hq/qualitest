package com.qualitest.flow.http;

import com.alibaba.fastjson2.JSONObject;
import com.qualitest.api.params.DebugHttpForwardParams;
import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.project.domain.TestProjectApi;
import com.qualitest.project.support.TestProjectApiEffectiveConfigResolver;
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
 * 测 FlowHttpRequestBuilder 薄节点：以 API 有效配置为底叠 requestValueOverrides；
 * 跑流不退回接口 body 默认。
 * 边界：内存 API/节点；忽略残留厚 requestConfig；无网络。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=FlowHttpRequestBuilderThinNodeTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlowHttpRequestBuilderThinNodeTest {

    /**
     * 前提：薄节点含 requestValueOverrides，API 有效配置为 GET；节点残留厚 requestConfig。
     * 期望：以 API 结构为底叠覆盖层，method 仍为 GET，忽略残留 POST 配置。
     */
    @Test
    @Order(1)
    @DisplayName("薄节点叠 overrides 且忽略厚配置")
    void build_usesApiStructure_andNodeOverrides() {
        TestProjectApi api = TestProjectApi.builder()
                .testProjectApiId(1L)
                .testProjectId(1L)
                .apiPath("/api/users")
                .requestConfig("""
                        {
                          "configVersion":1,
                          "method":"GET",
                          "queryParams":[
                            {"name":"mobile","type":"string","value":""},
                            {"name":"pageNum","type":"string","value":""}
                          ],
                          "pathParams":[],
                          "declaredHeaders":[],
                          "body":{"mode":"none"}
                        }
                        """)
                .testValueConfig("""
                        {"request":{"paramDefaults":{"mobile":"13900000000","pageNum":"1"}}}
                        """)
                .build();
        TestProjectApi effective = TestProjectApiEffectiveConfigResolver.resolve(api).toApiView(api);

        Map<String, Object> nodeData = new HashMap<>();
        nodeData.put("callMode", "project");
        nodeData.put("testProjectApiId", "1");
        nodeData.put("requestValueOverrides", Map.of(
                "paramDefaults", Map.of("mobile", "{{flow.mobile}}")
        ));
        // 残留厚配置应被忽略
        nodeData.put("requestConfig", Map.of(
                "method", "POST",
                "queryParams", java.util.List.of(Map.of("name", "mobile", "value", "SHOULD_IGNORE"))
        ));

        FlowRunContext ctx = FlowRunContext.builder()
                .env(Map.of("baseUrl", "http://localhost:8080"))
                .flow(Map.of("mobile", "13800001111"))
                .build();

        FlowHttpRequestBuilder.BuiltHttpRequest built =
                FlowHttpRequestBuilder.buildFromProject(ctx, effective, nodeData);

        assertEquals("GET", built.getMethod());
        assertTrue(built.getUrl().contains("mobile="));
        assertTrue(built.getUrl().contains("13800001111") || built.getUrl().contains("%7B%7Bflow.mobile%7D%7D")
                || built.getUrl().contains("{{flow.mobile}}")
                || built.getUrl().contains("13800001111"));
        // pageNum 来自资产默认，薄节点自动跟上
        assertTrue(built.getUrl().contains("pageNum"));
        assertFalse(built.getMethod().equals("POST"));
    }

    /**
     * 前提：基础 requestConfig 含空值 query/body，overrides 提供 paramDefaults 与 bodyExample。
     * 期望：overlay 后 JSON 含覆盖后的参数值与 body 示例字段。
     */
    @Test
    @Order(2)
    @DisplayName("overlay 应用参数与 body 示例")
    void overlayRequestValuesFromOverrides_appliesParamAndBody() {
        String base = """
                {"method":"POST","queryParams":[{"name":"q","value":""}],"pathParams":[],"declaredHeaders":[],
                 "body":{"mode":"json","json":{"example":""}}}
                """;
        JSONObject overrides = new JSONObject();
        overrides.put("paramDefaults", Map.of("q", "hello"));
        overrides.put("bodyExample", Map.of("a", 1));

        String overlaid = TestProjectApiEffectiveConfigResolver.overlayRequestValuesFromOverrides(base, overrides);
        assertTrue(overlaid.contains("hello"));
        assertTrue(overlaid.contains("\"a\":1") || overlaid.contains("\"a\": 1"));
    }

    /**
     * 前提：有效配置已叠入脏 TV body（items+cartIds），节点未写 bodyExample。
     * 期望：跑流不退回接口 body 默认，发出去的 JSON body 为空对象。
     */
    @Test
    @Order(3)
    @DisplayName("跑流：节点无 body 不退回接口默认")
    void build_withoutNodeBody_doesNotFallbackToApiBodyExample() {
        TestProjectApi api = TestProjectApi.builder()
                .testProjectApiId(10L)
                .testProjectId(1L)
                .apiPath("/api/mall/mallOrder/preview")
                .requestConfig("""
                        {
                          "configVersion":1,
                          "method":"POST",
                          "queryParams":[],
                          "pathParams":[],
                          "declaredHeaders":[],
                          "body":{"mode":"json","json":{"example":{}}}
                        }
                        """)
                .testValueConfig("""
                        {"request":{"bodyExample":{"addressId":4001,"cartIds":[5001],"items":[{"skuId":1}]}}}
                        """)
                .build();
        TestProjectApi effective = TestProjectApiEffectiveConfigResolver.resolve(api).toApiView(api);
        assertTrue(effective.getRequestConfig().contains("items"));

        Map<String, Object> nodeData = new HashMap<>();
        nodeData.put("callMode", "project");
        nodeData.put("testProjectApiId", "10");

        FlowRunContext ctx = FlowRunContext.builder()
                .env(Map.of("baseUrl", "http://localhost:8081"))
                .build();

        FlowHttpRequestBuilder.BuiltHttpRequest built =
                FlowHttpRequestBuilder.buildFromProject(ctx, effective, nodeData);

        DebugHttpForwardParams.DebugBodySpec body = built.getForwardParams().getBody();
        assertNotNull(body);
        assertEquals("json", body.getKind());
        assertEquals("{}", body.getRaw());
        assertFalse(body.getRaw().contains("items"));
        assertFalse(body.getRaw().contains("cartIds"));
    }

    /**
     * 前提：有效配置含脏默认；节点 requestValueOverrides.bodyExample 为合法 cartIds 路径。
     * 期望：发出去的 body 整段以节点为准，不含 items。
     */
    @Test
    @Order(4)
    @DisplayName("跑流：节点 body 整段替换接口默认")
    void build_withNodeBody_replacesApiBodyExampleWholly() {
        TestProjectApi api = TestProjectApi.builder()
                .testProjectApiId(11L)
                .testProjectId(1L)
                .apiPath("/api/mall/mallOrder/preview")
                .requestConfig("""
                        {
                          "configVersion":1,
                          "method":"POST",
                          "queryParams":[],
                          "pathParams":[],
                          "declaredHeaders":[],
                          "body":{"mode":"json","json":{"example":{"items":[{"skuId":0}],"cartIds":[0]}}}
                        }
                        """)
                .testValueConfig("""
                        {"request":{"bodyExample":{"addressId":1,"cartIds":[0],"items":[{"skuId":0}]}}}
                        """)
                .build();
        TestProjectApi effective = TestProjectApiEffectiveConfigResolver.resolve(api).toApiView(api);

        Map<String, Object> nodeData = new HashMap<>();
        nodeData.put("callMode", "project");
        nodeData.put("testProjectApiId", "11");
        nodeData.put("requestValueOverrides", Map.of(
                "bodyExample", Map.of(
                        "addressId", 4001,
                        "cartIds", List.of(5001, 5002)
                )
        ));

        FlowRunContext ctx = FlowRunContext.builder()
                .env(Map.of("baseUrl", "http://localhost:8081"))
                .build();

        FlowHttpRequestBuilder.BuiltHttpRequest built =
                FlowHttpRequestBuilder.buildFromProject(ctx, effective, nodeData);

        DebugHttpForwardParams.DebugBodySpec body = built.getForwardParams().getBody();
        assertNotNull(body);
        assertTrue(body.getRaw().contains("4001"));
        assertTrue(body.getRaw().contains("5001"));
        assertFalse(body.getRaw().contains("items"));
    }

    /**
     * 前提：接口 inherit、项目双端配置、节点无 Authorization；flow.token 已写入。
     * 期望：发送头含 Bearer 解析后的 token；显式非托管头不被刷新覆盖。
     */
    @Test
    @Order(4)
    @DisplayName("Run 按项目鉴权补 Authorization 且显式头优先")
    void build_injectsAuthHeader_andKeepsExplicit() {
        TestProjectApi api = TestProjectApi.builder()
                .testProjectApiId(21L)
                .testProjectId(1L)
                .apiPath("/api/account/auth/profile")
                .authConfig("{\"mode\":\"inherit\"}")
                .requestConfig("""
                        {"configVersion":1,"method":"GET","queryParams":[],"pathParams":[],"body":{"mode":"none"}}
                        """)
                .build();
        TestProjectApi effective = TestProjectApiEffectiveConfigResolver.resolve(api).toApiView(api);
        String projectAuth = com.qualitest.api.util.ProjectAuthConfigSupport.toJson(
                com.qualitest.api.util.AuthProfileTestFixtures.adminThenClient());

        Map<String, Object> nodeData = new HashMap<>();
        nodeData.put("callMode", "project");
        nodeData.put("testProjectApiId", "21");

        FlowRunContext ctx = FlowRunContext.builder()
                .env(Map.of("baseUrl", "http://localhost:8081"))
                .flow(new HashMap<>(Map.of("token", "tok-client")))
                .projectAuthConfig(projectAuth)
                .build();

        FlowHttpRequestBuilder.BuiltHttpRequest built =
                FlowHttpRequestBuilder.buildFromProject(ctx, effective, nodeData);
        assertEquals("Bearer tok-client", built.getForwardParams().getHeaders().stream()
                .filter(h -> "Authorization".equalsIgnoreCase(h.getName()))
                .findFirst()
                .orElseThrow()
                .getValue());

        Map<String, Object> explicitRow = new HashMap<>();
        explicitRow.put("_enabled", true);
        explicitRow.put("name", "Authorization");
        explicitRow.put("value", "Bearer {{flow.adminToken}}");
        nodeData.put("headers", List.of(explicitRow));
        ctx.getFlow().put("adminToken", "tok-admin");

        FlowHttpRequestBuilder.BuiltHttpRequest builtExplicit =
                FlowHttpRequestBuilder.buildFromProject(ctx, effective, nodeData);
        assertEquals("Bearer tok-admin", builtExplicit.getForwardParams().getHeaders().stream()
                .filter(h -> "Authorization".equalsIgnoreCase(h.getName()))
                .findFirst()
                .orElseThrow()
                .getValue());
    }

    /**
     * 前提：query 含有值 pageNum 与空值 params（部分框架 BaseEntity 常见）。
     * 期望：URL 仅带 pageNum，不出现 params=（避免被测端 Map 绑定 500）。
     */
    @Test
    @Order(5)
    @DisplayName("跑流：跳过空值 query（含 params）")
    void build_skipsBlankQueryParams() {
        TestProjectApi api = TestProjectApi.builder()
                .testProjectApiId(30L)
                .testProjectId(1L)
                .apiPath("/web/mall/mallOrder/list")
                .requestConfig("""
                        {
                          "configVersion":1,
                          "method":"GET",
                          "queryParams":[
                            {"name":"pageNum","type":"string","value":"1","_enabled":true},
                            {"name":"params","type":"string","value":"","_enabled":true}
                          ],
                          "pathParams":[],
                          "declaredHeaders":[],
                          "body":{"mode":"none"}
                        }
                        """)
                .build();

        Map<String, Object> nodeData = new HashMap<>();
        nodeData.put("callMode", "project");
        nodeData.put("testProjectApiId", "30");

        FlowRunContext ctx = FlowRunContext.builder()
                .env(Map.of("baseUrl", "http://localhost:8081"))
                .build();

        FlowHttpRequestBuilder.BuiltHttpRequest built =
                FlowHttpRequestBuilder.buildFromProject(ctx, api, nodeData);

        assertTrue(built.getUrl().contains("pageNum=1"));
        assertFalse(built.getUrl().contains("params="));
    }
}
