package com.qualitest.ai.scenario.flow;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.project.domain.TestProjectApi;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FlowDesignHttpNodeNormalizerTest {

    @Test
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

    @Test
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

    @Test
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

    @Test
    void normalize_extractsValueConvertedToExprWithDataPrefix() {
        Map<String, Object> data = new HashMap<>();
        data.put("callMode", "project");
        data.put("extracts", List.of(Map.of("name", "mobile", "value", "responses.mobile")));

        FlowDesignHttpNodeNormalizer.normalize(data, null);

        JSONArray extracts = (JSONArray) data.get("extracts");
        assertEquals(1, extracts.size());
        JSONObject row = extracts.getJSONObject(0);
        assertEquals("$.data.mobile", row.getString("expr"));
        assertEquals("body", row.getString("from"));
        assertEquals("flow", row.getString("scope"));
        assertEquals("mobile", row.getString("name"));
        assertFalse(row.containsKey("value"));
    }

    @Test
    void convertLegacyExtractExpr_handlesCommonPrefixes() {
        assertEquals("$.mobile", FlowDesignHttpNodeNormalizer.convertLegacyExtractExpr("responses.mobile"));
        assertEquals("$.data.token", FlowDesignHttpNodeNormalizer.convertLegacyExtractExpr("http.body.data.token"));
        assertEquals("$.token", FlowDesignHttpNodeNormalizer.convertLegacyExtractExpr("$.token"));
    }

    @Test
    void ensureDataPathPrefix_shallowAndRootFields() {
        assertEquals("$.data.mobile", FlowDesignHttpNodeNormalizer.ensureDataPathPrefix("$.mobile"));
        assertEquals("$.data.token", FlowDesignHttpNodeNormalizer.ensureDataPathPrefix("$.data.token"));
        assertEquals("$.code", FlowDesignHttpNodeNormalizer.ensureDataPathPrefix("$.code"));
        assertEquals("$.msg", FlowDesignHttpNodeNormalizer.ensureDataPathPrefix("$.msg"));
        assertEquals("$.data", FlowDesignHttpNodeNormalizer.ensureDataPathPrefix("$.data"));
    }

    @Test
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
