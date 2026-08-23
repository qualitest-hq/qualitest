package com.qualitest.api.util;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测值剥离：结构里的 value / example 应进测值配置，结构侧不再保留。
 */
class ApiTestValuePeelSupportTest {

    @Test
    @DisplayName("参数 value 与 body example 进测值，结构侧删掉")
    void peelRequestValues() throws Exception {
        String request = """
                {"configVersion":1,"method":"POST",
                 "queryParams":[{"name":"q","value":"hello","type":"string"}],
                 "body":{"mode":"json","json":{"schema":{"type":"object"},"example":{"u":"a"}}}}
                """;
        ApiTestValuePeelSupport.PeelResult r = ApiTestValuePeelSupport.peel(request, "{}", null);
        JsonNode req = ApiConfigJsonSupport.readTree(r.getRequestConfig());
        assertFalse(req.path("queryParams").path(0).has("value"));
        assertFalse(req.path("body").path("json").has("example"));
        JsonNode tv = ApiConfigJsonSupport.readTree(r.getTestValueConfig());
        assertEquals("hello", tv.path("request").path("paramDefaults").path("q").asText());
        assertEquals("a", tv.path("request").path("bodyExample").path("u").asText());
    }

    @Test
    @DisplayName("响应 example 进 examplesById，结构侧删掉")
    void peelResponseExamples() throws Exception {
        String response = """
                {"configVersion":1,"responses":[
                  {"id":"resp-ok","name":"成功","httpStatus":200,"contentType":"json",
                   "schema":{"type":"object"},"example":{"code":200}}
                ]}
                """;
        ApiTestValuePeelSupport.PeelResult r = ApiTestValuePeelSupport.peel("{}", response, null);
        JsonNode resp = ApiConfigJsonSupport.readTree(r.getResponseConfig());
        assertFalse(resp.path("responses").path(0).has("example"));
        JsonNode tv = ApiConfigJsonSupport.readTree(r.getTestValueConfig());
        assertEquals(200, tv.path("response").path("examplesById").path("resp-ok").path("code").asInt());
        assertTrue(r.getRequestConfig().contains("configVersion") || r.getRequestConfig().equals("{}")
                || r.getRequestConfig().contains("{"));
    }
}
