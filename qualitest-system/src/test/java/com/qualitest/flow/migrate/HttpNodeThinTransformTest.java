package com.qualitest.flow.migrate;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.project.domain.TestProjectApi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测 HttpNodeThinTransform：厚 HTTP 节点 → 薄节点（抽测值、删 requestConfig）。
 * 边界：内存图与 API；跳过 external；无 DB。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=HttpNodeThinTransformTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HttpNodeThinTransformTest {

    /**
     * 前提：project 模式厚 HTTP 节点含 requestConfig 与 apiPath，且能解析对应 API。
     * 期望：抽取测值到 requestValueOverrides，移除 requestConfig/apiPath，标记 changed。
     */
    @Test
    @Order(1)
    @DisplayName("厚节点抽取测值并移除 requestConfig")
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

    /**
     * 前提：external 模式 HTTP 节点含 requestConfig。
     * 期望：不做变换，changed 为 false，requestConfig 保留。
     */
    @Test
    @Order(2)
    @DisplayName("external 节点跳过变换")
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

    /**
     * 前提：节点已是薄节点（无 requestConfig，含 requestValueOverrides）。
     * 期望：再次变换无变更，changed 为 false。
     */
    @Test
    @Order(3)
    @DisplayName("薄节点再次变换幂等")
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
