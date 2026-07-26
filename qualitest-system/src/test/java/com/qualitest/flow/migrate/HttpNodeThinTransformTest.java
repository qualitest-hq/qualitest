package com.qualitest.flow.migrate;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.project.domain.TestProjectApi;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 厚节点 → 薄节点变换单测：抽测值、删 requestConfig / apiPath、跳过 external。 */
class HttpNodeThinTransformTest {

    @Test
    void transform_extractsValuesAndRemovesRequestConfig() {
        String graph = """
                {
                  "nodes": [
                    {
                      "id": "n1",
                      "type": "http",
                      "data": {
                        "callMode": "project",
                        "name": "登录",
                        "testProjectApiId": "100",
                        "apiPath": "/api/login",
                        "requestConfig": {
                          "method": "POST",
                          "queryParams": [{"name": "mobile", "value": "{{flow.mobile}}"}],
                          "pathParams": [],
                          "declaredHeaders": [],
                          "body": {"mode": "json", "json": {"example": {"password": "x"}}}
                        }
                      }
                    }
                  ],
                  "edges": []
                }
                """;
        TestProjectApi api = TestProjectApi.builder()
                .testProjectApiId(100L)
                .apiPath("/api/login")
                .apiName("登录")
                .requestConfig("{\"method\":\"POST\"}")
                .build();

        HttpNodeThinTransform.TransformResult result = HttpNodeThinTransform.transformGraphJson(
                graph, id -> api);

        assertTrue(result.changed);
        assertEquals(1, result.nodeChanges.size());
        assertTrue(result.nodeChanges.get(0).removedRequestConfig);
        assertTrue(result.nodeChanges.get(0).clearedApiPath);

        JSONObject root = JSON.parseObject(result.transformedJson);
        JSONObject data = root.getJSONArray("nodes").getJSONObject(0).getJSONObject("data");
        assertNull(data.get("requestConfig"));
        assertNull(data.get("apiPath"));
        JSONObject overrides = data.getJSONObject("requestValueOverrides");
        assertEquals("{{flow.mobile}}", overrides.getJSONObject("paramDefaults").getString("mobile"));
        assertTrue(overrides.containsKey("bodyExample"));
    }

    @Test
    void transform_skipsExternalNodes() {
        String graph = """
                {
                  "nodes": [{
                    "id": "e1",
                    "type": "http",
                    "data": {
                      "callMode": "external",
                      "externalUrl": "https://example.com",
                      "requestConfig": {"method": "GET"}
                    }
                  }],
                  "edges": []
                }
                """;
        HttpNodeThinTransform.TransformResult result = HttpNodeThinTransform.transformGraphJson(
                graph, id -> null);
        assertFalse(result.changed);
        JSONObject data = JSON.parseObject(result.transformedJson)
                .getJSONArray("nodes").getJSONObject(0).getJSONObject("data");
        assertTrue(data.containsKey("requestConfig"));
    }

    @Test
    void transform_idempotentOnThinNode() {
        String graph = """
                {
                  "nodes": [{
                    "id": "n1",
                    "type": "http",
                    "data": {
                      "callMode": "project",
                      "testProjectApiId": "1",
                      "httpMethod": "GET",
                      "requestValueOverrides": {"paramDefaults": {"q": "1"}}
                    }
                  }],
                  "edges": []
                }
                """;
        HttpNodeThinTransform.TransformResult result = HttpNodeThinTransform.transformGraphJson(
                graph, id -> null);
        assertFalse(result.changed);
    }
}
