package com.qualitest.ai.scenario.flow;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;


/**
 * 测 FlowDesignHttpNodeNormalizer：AI patch 里 HTTP 节点瘦身（body → overrides，剥厚 requestConfig）。
 * 边界：纯函数，依赖传入的 TestProjectApi，无 DB。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=FlowDesignHttpNodeNormalizerTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlowDesignHttpNodeNormalizerTest {

    /**
     * 前提：节点带 requestBody JSON，项目 API 含 method=POST。
     * 期望：去掉 requestBody/requestConfig/apiPath；写入 httpMethod 与 bodyExample overrides；successCheck=inherit。
     */
    @Test
    @Order(1)
    @DisplayName("requestBody 写入 overrides 并剥厚字段")
    void normalize_writesRequestBodyToOverrides_notRequestConfig() {
        TestProjectApi api = TestProjectApi.builder()
                .testProjectApiId(2001L)
                .apiName("客户端用户登录")
                .apiPath("/api/account/auth/login")
                .requestConfig("""
                        {"configVersion":1,"body":{"mode":"json","json":{"example":{"mobile":"","password":""}}},"method":"POST","queryParams":[],"pathParams":[],"declaredHeaders":[]}
                        """)
                .build();

        Map<String, Object> data = new HashMap<>();
        data.put("callMode", "project");
        data.put("testProjectApiId", "2001");
        data.put("apiPath", "/api/account/auth/login");
        data.put("requestBody", "{\"mobile\":\"13800000001\",\"password\":\"Test@123456\"}");

        FlowDesignHttpNodeNormalizer.normalize(data, api);

        assertNull(data.get("requestBody"));
        assertNull(data.get("requestConfig"));
        assertNull(data.get("apiPath"));
        assertEquals("POST", data.get("httpMethod"));
        @SuppressWarnings("unchecked")
        Map<String, Object> overrides = (Map<String, Object>) data.get("requestValueOverrides");
        assertTrue(overrides.containsKey("bodyExample"));
        @SuppressWarnings("unchecked")
        Map<String, Object> successCheck = (Map<String, Object>) data.get("successCheck");
        assertEquals("inherit", successCheck.get("mode"));
    }

    /**
     * 前提：节点带厚 requestConfig，query 值与 API 默认不同。
     * 期望：剥掉 requestConfig，仅保留与默认的 diff overrides。
     */
    @Test
    @Order(2)
    @DisplayName("厚 requestConfig 仅保留 diff overrides")
    void normalize_stripsThickRequestConfig_keepsDiffOverrides() {
        TestProjectApi api = TestProjectApi.builder()
                .testProjectApiId(1L)
                .apiPath("/api/demo")
                .requestConfig("""
                        {"configVersion":1,"method":"GET","queryParams":[{"name":"mobile","type":"string","value":""}],"pathParams":[],"declaredHeaders":[],"body":{"mode":"none"}}
                        """)
                .testValueConfig("""
                        {"request":{"paramDefaults":{"mobile":"13900000000"}}}
                        """)
                .build();

        Map<String, Object> data = new HashMap<>();
        data.put("callMode", "project");
        data.put("testProjectApiId", "1");
        data.put("requestConfig", Map.of(
                "method", "GET",
                "queryParams", List.of(Map.of("name", "mobile", "value", "{{flow.mobile}}")),
                "pathParams", List.of(),
                "body", Map.of("mode", "none")
        ));

        FlowDesignHttpNodeNormalizer.normalize(data, api);

        assertNull(data.get("requestConfig"));
        @SuppressWarnings("unchecked")
        Map<String, Object> overrides = (Map<String, Object>) data.get("requestValueOverrides");
        @SuppressWarnings("unchecked")
        Map<String, Object> params = (Map<String, Object>) overrides.get("paramDefaults");
        assertEquals("{{flow.mobile}}", params.get("mobile"));
    }

    /**
     * 前提：节点 query 值与资产默认相同。
     * 期望：剥掉 requestConfig，且不产生 requestValueOverrides。
     */
    @Test
    @Order(3)
    @DisplayName("与资产默认相同则不产生 overrides")
    void normalize_sameAsAssetDefaults_noOverrides() {
        TestProjectApi api = TestProjectApi.builder()
                .testProjectApiId(1L)
                .apiPath("/api/demo")
                .requestConfig("""
                        {"configVersion":1,"method":"GET","queryParams":[{"name":"q","type":"string"}],"pathParams":[],"declaredHeaders":[],"body":{"mode":"none"}}
                        """)
                .testValueConfig("""
                        {"request":{"paramDefaults":{"q":"hello"}}}
                        """)
                .build();

        Map<String, Object> data = new HashMap<>();
        data.put("callMode", "project");
        data.put("requestConfig", Map.of(
                "queryParams", List.of(Map.of("name", "q", "value", "hello"))
        ));

        FlowDesignHttpNodeNormalizer.normalize(data, api);

        assertNull(data.get("requestConfig"));
        assertNull(data.get("requestValueOverrides"));
    }

    /**
     * 前提：extracts 使用旧字段 value=responses.mobile。
     * 期望：转为 expr=$.mobile（不补 data 前缀）、from=body、scope=flow，并去掉 value。
     */
    @Test
    @Order(4)
    @DisplayName("旧 extracts value 转为 $. 表达式且不补 data")
    void normalize_extractsValueConvertedToExprWithoutDataPrefix() {
        Map<String, Object> data = new HashMap<>();
        data.put("callMode", "project");
        data.put("extracts", List.of(Map.of("name", "mobile", "value", "responses.mobile")));

        FlowDesignHttpNodeNormalizer.normalize(data, null);

        JSONArray extracts = (JSONArray) data.get("extracts");
        assertEquals(1, extracts.size());
        JSONObject row = extracts.getJSONObject(0);
        assertEquals("$.mobile", row.getString("expr"));
        assertEquals("body", row.getString("from"));
        assertEquals("flow", row.getString("scope"));
        assertEquals("mobile", row.getString("name"));
        assertFalse(row.containsKey("value"));
    }

    /**
     * 前提：旧式 responses.* / http.body.data.* / 已是 $. 的表达式。
     * 期望：统一成 $. 路径。
     */
    @Test
    @Order(5)
    @DisplayName("旧式提取表达式统一为 $. 路径")
    void convertLegacyExtractExpr_handlesCommonPrefixes() {
        assertEquals("$.mobile", FlowDesignHttpNodeNormalizer.convertLegacyExtractExpr("responses.mobile"));
        assertEquals("$.data.token", FlowDesignHttpNodeNormalizer.convertLegacyExtractExpr("http.body.data.token"));
        assertEquals("$.token", FlowDesignHttpNodeNormalizer.convertLegacyExtractExpr("$.token"));
    }

    /**
     * 前提：单段 $.token、$.mobile 与已有 $.data.token。
     * 期望：规范化不改写这些路径。
     */
    @Test
    @Order(6)
    @DisplayName("提取路径保持原样不补 data")
    void normalizeExtracts_keepsExprUnchanged() {
        Map<String, Object> data = new HashMap<>();
        data.put("callMode", "project");
        data.put("extracts", List.of(
                Map.of("name", "adminToken", "expr", "$.token"),
                Map.of("name", "token", "expr", "$.data.token"),
                Map.of("name", "mobile", "expr", "$.mobile")
        ));

        FlowDesignHttpNodeNormalizer.normalizeExtracts(data);

        JSONArray extracts = (JSONArray) data.get("extracts");
        assertEquals("$.token", extracts.getJSONObject(0).getString("expr"));
        assertEquals("$.data.token", extracts.getJSONObject(1).getString("expr"));
        assertEquals("$.mobile", extracts.getJSONObject(2).getString("expr"));
    }

    /**
     * 前提：callMode=external。
     * 期望：successCheck.mode=off。
     */
    @Test
    @Order(7)
    @DisplayName("external 模式默认 successCheck 为 off")
    void normalize_external_defaultsSuccessCheckOff() {
        Map<String, Object> data = new HashMap<>();
        data.put("callMode", "external");
        data.put("externalUrl", "https://example.com");

        FlowDesignHttpNodeNormalizer.normalize(data, null);

        @SuppressWarnings("unchecked")
        Map<String, Object> successCheck = (Map<String, Object>) data.get("successCheck");
        assertEquals("off", successCheck.get("mode"));
    }
}
