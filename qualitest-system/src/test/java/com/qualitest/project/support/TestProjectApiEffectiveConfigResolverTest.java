package com.qualitest.project.support;

import com.qualitest.project.domain.TestProjectApi;
import com.qualitest.project.result.TestProjectApiResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测 TestProjectApiEffectiveConfigResolver：test_value 叠回请求/响应有效配置。
 * 边界：null 安全、Result 就地替换、配置字段副本；无 DB。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=TestProjectApiEffectiveConfigResolverTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TestProjectApiEffectiveConfigResolverTest {

    /**
     * 前提：API 含 query 参数与 body example，test_value_config 提供 paramDefaults/bodyExample。
     * 期望：有效 requestConfig 含覆盖后的 mobile 默认值与 username body 示例。
     */
    @Test
    @Order(1)
    @DisplayName("resolve：叠回参数默认值与 body example")
    void resolve_overlaysParamDefaultsAndBodyExample() {
        TestProjectApi api = TestProjectApi.builder()
                .requestConfig("""
                        {
                          "configVersion": 1,
                          "method": "POST",
                          "queryParams": [{"name": "mobile", "type": "string"}],
                          "pathParams": [],
                          "declaredHeaders": [],
                          "body": {"mode": "json", "json": {"schema": null, "example": "{\\"a\\":1}"}}
                        }
                        """)
                .testValueConfig("""
                        {
                          "request": {
                            "paramDefaults": {"mobile": "13900000002"},
                            "bodyExample": "{\\"username\\":\\"admin\\"}"
                          }
                        }
                        """)
                .build();

        var effective = TestProjectApiEffectiveConfigResolver.resolve(api);
        assertTrue(effective.getRequestConfig().contains("13900000002"));
        assertTrue(effective.getRequestConfig().contains("admin"));
    }

    /**
     * 前提：response id 匹配，test_value_config 含 examplesById 示例。
     * 期望：有效 responseConfig 含覆盖后的 code:14 示例内容。
     */
    @Test
    @Order(2)
    @DisplayName("resolve：按 response id 叠回 example")
    void resolve_overlaysResponseExampleById() {
        String respId = "resp-001";
        TestProjectApi api = TestProjectApi.builder()
                .requestConfig("""
                        {"configVersion":1,"method":"GET","queryParams":[],"pathParams":[],
                         "declaredHeaders":[],"body":{"mode":"none"}}
                        """)
                .responseConfig(String.format("""
                        {
                          "configVersion": 1,
                          "responses": [
                            {"id": "%s", "name": "成功", "httpStatus": 200, "contentType": "json", "schema": null}
                          ]
                        }
                        """, respId))
                .testValueConfig(String.format("""
                        {
                          "response": {
                            "examplesById": {
                              "%s": {"code": 14, "msg": "ok"}
                            }
                          }
                        }
                        """, respId))
                .build();

        var effective = TestProjectApiEffectiveConfigResolver.resolve(api);
        assertTrue(effective.getResponseConfig().contains("\"code\":14"));
    }

    /**
     * 前提：resolve 入参 api 为 null。
     * 期望：request/response 均返回空 JSON 占位 {}，不抛异常。
     */
    @Test
    @Order(3)
    @DisplayName("resolve：api 为 null 时返回空 JSON")
    void resolve_nullApi_returnsEmptyJson() {
        var effective = TestProjectApiEffectiveConfigResolver.resolve(null);
        assertEquals("{}", effective.getRequestConfig());
        assertEquals("{}", effective.getResponseConfig());
    }

    /**
     * 前提：Result 含 request/response 与 testValueConfig 默认值。
     * 期望：overlay 后 request 含默认值，testValueConfig 列保持原值不变。
     */
    @Test
    @Order(4)
    @DisplayName("overlayResult：替换 request/response，保留 testValueConfig")
    void overlayResultConfigs_replacesRequestAndResponse() {
        TestProjectApiResult result = TestProjectApiResult.builder()
                .requestConfig("""
                        {
                          "configVersion": 1,
                          "method": "GET",
                          "queryParams": [{"name": "id", "type": "string"}],
                          "pathParams": [],
                          "declaredHeaders": [],
                          "body": {"mode": "none"}
                        }
                        """)
                .responseConfig("""
                        {"configVersion":1,"responses":[
                          {"id":"r1","name":"成功","httpStatus":200,"contentType":"json","schema":null}
                        ]}
                        """)
                .testValueConfig("""
                        {"request":{"paramDefaults":{"id":"999"}}}
                        """)
                .build();
        String rawTestValue = result.getTestValueConfig();

        TestProjectApiEffectiveConfigResolver.overlayResultConfigs(result);

        assertTrue(result.getRequestConfig().contains("999"));
        assertEquals(rawTestValue, result.getTestValueConfig());
    }

    /**
     * 前提：节点 overrides 把 cartIds/addressId 误放在顶层（无 bodyExample）。
     * 期望：叠层后请求体 example 含这些字段（纠正后生效，不再静默丢弃）。
     */
    @Test
    @Order(5)
    @DisplayName("overlayOverrides：顶层误放 body 字段仍能叠进请求体")
    void overlayRequestValuesFromOverrides_liftsStrayBodyFields() {
        String base = """
                {
                  "configVersion": 1,
                  "method": "POST",
                  "queryParams": [],
                  "pathParams": [],
                  "declaredHeaders": [],
                  "body": {"mode": "json", "json": {"example": {"items":[{"skuId":0}],"cartIds":[0]}}}
                }
                """;
        var overrides = java.util.Map.of(
                "cartIds", java.util.List.of(5001, 5002),
                "addressId", 4001
        );

        String overlaid = TestProjectApiEffectiveConfigResolver.overlayRequestValuesFromOverrides(base, overrides);

        assertTrue(overlaid.contains("5001"));
        assertTrue(overlaid.contains("4001"));
        assertTrue(overlaid.contains("addressId"));
    }

    /**
     * 前提：request 含 body.json.example 脏默认。
     * 期望：stripBodyExample 后 example 被移除；resolve 仍叠回 TV body（调试路径不变）。
     */
    @Test
    @Order(6)
    @DisplayName("stripBodyExample：剥掉 example；resolve 仍叠 TV body")
    void stripBodyExample_removesExample_resolveStillOverlaysTvBody() {
        String withExample = """
                {
                  "configVersion": 1,
                  "method": "POST",
                  "queryParams": [],
                  "pathParams": [],
                  "declaredHeaders": [],
                  "body": {"mode": "json", "json": {"example": {"items":[{"skuId":0}],"cartIds":[0]}}}
                }
                """;
        String stripped = TestProjectApiEffectiveConfigResolver.stripBodyExample(withExample);
        assertFalse(stripped.contains("items"));
        assertFalse(stripped.contains("cartIds"));
        assertFalse(stripped.contains("\"example\""));

        TestProjectApi api = TestProjectApi.builder()
                .requestConfig("""
                        {
                          "configVersion": 1,
                          "method": "POST",
                          "queryParams": [],
                          "pathParams": [],
                          "declaredHeaders": [],
                          "body": {"mode": "json", "json": {"schema": null, "example": "{}"}}
                        }
                        """)
                .testValueConfig("""
                        {"request":{"bodyExample":{"addressId":4001,"cartIds":[5001],"items":[{"skuId":0}]}}}
                        """)
                .build();
        var effective = TestProjectApiEffectiveConfigResolver.resolve(api);
        assertTrue(effective.getRequestConfig().contains("cartIds"));
        assertTrue(effective.getRequestConfig().contains("items"));
    }

    /**
     * 前提：源 API 含身份字段与 testValueConfig 默认值。
     * 期望：toApiView 保留 id/path/headers，requestConfig 为有效配置（含 hello）。
     */
    @Test
    @Order(7)
    @DisplayName("toApiView：保留身份字段并用有效配置")
    void toApiView_usesEffectiveRequestAndResponse() {
        TestProjectApi source = TestProjectApi.builder()
                .testProjectApiId(100L)
                .testProjectId(200L)
                .apiPath("/api/demo")
                .requestConfig("""
                        {
                          "configVersion": 1,
                          "method": "GET",
                          "queryParams": [{"name": "q", "type": "string"}],
                          "pathParams": [],
                          "declaredHeaders": [],
                          "body": {"mode": "none"}
                        }
                        """)
                .testValueConfig("{\"request\":{\"paramDefaults\":{\"q\":\"hello\"}}}")
                .headers("{\"H\":\"1\"}")
                .build();

        var effective = TestProjectApiEffectiveConfigResolver.resolve(source);
        TestProjectApi view = effective.toApiView(source);

        assertEquals(100L, view.getTestProjectApiId());
        assertEquals("/api/demo", view.getApiPath());
        assertTrue(view.getRequestConfig().contains("hello"));
        assertEquals("{\"H\":\"1\"}", view.getHeaders());
    }
}
