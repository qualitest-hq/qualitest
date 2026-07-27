package com.qualitest.flow.http;

import com.alibaba.fastjson2.JSONObject;
import com.qualitest.flow.context.FlowRunContext;
import com.qualitest.project.domain.TestProjectApi;
import com.qualitest.project.support.TestProjectApiEffectiveConfigResolver;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 薄节点运行时：以 API 有效配置为底，叠 requestValueOverrides；忽略残留 requestConfig。
 * <p>
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=FlowHttpRequestBuilderThinNodeTest
 */
class FlowHttpRequestBuilderThinNodeTest {

    /**
     * 前提：薄节点含 requestValueOverrides，API 有效配置为 GET；节点残留厚 requestConfig。
     * 期望：以 API 结构为底叠覆盖层，method 仍为 GET，忽略残留 POST 配置。
     */
    @Test
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
}
