package com.qualitest.project.support;

import com.qualitest.project.domain.TestProjectApi;
import com.qualitest.project.result.TestProjectApiResult;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static com.qualitest.flow.support.FlowTestSections.begin;
import static com.qualitest.flow.support.FlowTestSections.end;
import static com.qualitest.flow.support.FlowTestSections.log;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 有效配置合成单元测试。
 * <p>
 * 覆盖：参数默认值与 body 示例叠回请求、响应 example 按 id 叠回、null 安全、
 * Result 就地替换、以及生成仅替换配置字段的 API 副本。
 * <p>
 * 运行（qualitest 目录）：mvn test -pl qualitest-system -am -DskipTests=false -Dtest=TestProjectApiEffectiveConfigResolverTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TestProjectApiEffectiveConfigResolverTest {

    /**
     * paramDefaults 与 bodyExample 应写入合成后的 request_config 对应字段。
     */
    @Test
    @Order(1)
    void resolve_overlaysParamDefaultsAndBodyExample() {
        begin("resolve_overlaysParamDefaultsAndBodyExample");
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
        log("paramDefaults + bodyExample overlaid");
        end("resolve_overlaysParamDefaultsAndBodyExample");
    }

    /**
     * test_value_config.response.examplesById 应按 response id 回填 responses[].example。
     */
    @Test
    @Order(2)
    void resolve_overlaysResponseExampleById() {
        begin("resolve_overlaysResponseExampleById");
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
        log("response example overlaid for id=" + respId);
        end("resolve_overlaysResponseExampleById");
    }

    /** 入参 api 为 null 时返回空 JSON 占位，避免 NPE。 */
    @Test
    @Order(3)
    void resolve_nullApi_returnsEmptyJson() {
        begin("resolve_nullApi_returnsEmptyJson");
        var effective = TestProjectApiEffectiveConfigResolver.resolve(null);
        assertEquals("{}", effective.getRequestConfig());
        assertEquals("{}", effective.getResponseConfig());
        log("null api -> empty configs");
        end("resolve_nullApi_returnsEmptyJson");
    }

    /**
     * overlayResultConfigs 应就地替换 Result 的 request/response，testValueConfig 列保持原值。
     */
    @Test
    @Order(4)
    void overlayResultConfigs_replacesRequestAndResponse() {
        begin("overlayResultConfigs_replacesRequestAndResponse");
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
        log("result overlaid; testValueConfig unchanged");
        end("overlayResultConfigs_replacesRequestAndResponse");
    }

    /**
     * toApiView 应复制源 API 的身份字段，并用有效配置替换 request/response 及覆盖层字段。
     */
    @Test
    @Order(5)
    void toApiView_usesEffectiveRequestAndResponse() {
        begin("toApiView_usesEffectiveRequestAndResponse");
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
        log("toApiView identity + effective request");
        end("toApiView_usesEffectiveRequestAndResponse");
    }
}
