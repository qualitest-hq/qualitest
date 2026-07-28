package com.qualitest.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qualitest.project.domain.TestProjectApi;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测 ApiImportMergeService：导入结构合并（参数默认值、响应 example、soft merge）。
 * 边界：本地 vs 上传包差异；纯函数，无 DB。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=ApiImportMergeServiceTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApiImportMergeServiceTest {

    private final ApiImportMergeService service = new ApiImportMergeService();
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 前提：本地 query 参数含 value，上传包新增 pageNum。
     * 期望：默认值迁入 test_value_config，结构层无 value，summary 记录 pageNum 新增。
     */
    @Test
    @Order(1)
    @DisplayName("合并：参数默认值迁入 test_value，并记录新增 query")
    void merge_migratesParamDefaultsAndAddsQueryParam() {
        TestProjectApi existing = TestProjectApi.builder()
                .requestConfig("""
                        {
                          "configVersion": 1,
                          "method": "GET",
                          "queryParams": [
                            {"name": "mobile", "type": "string", "value": "13800000001"}
                          ],
                          "pathParams": [],
                          "declaredHeaders": [],
                          "body": {"mode": "none"}
                        }
                        """)
                .responseConfig("""
                        {
                          "configVersion": 1,
                          "responses": [
                            {"id": "resp-old", "name": "成功", "httpStatus": 200, "contentType": "json", "schema": null}
                          ]
                        }
                        """)
                .build();

        String incomingRequest = """
                {
                  "configVersion": 1,
                  "method": "GET",
                  "queryParams": [
                    {"name": "mobile", "type": "string", "required": false, "description": "手机号"},
                    {"name": "pageNum", "type": "integer", "required": false}
                  ],
                  "pathParams": [],
                  "declaredHeaders": [],
                  "body": {"mode": "none", "json": {"schema": null, "example": null}}
                }
                """;
        String incomingResponse = """
                {
                  "configVersion": 1,
                  "responses": [
                    {"id": "resp-new", "name": "成功", "httpStatus": 200, "contentType": "json",
                     "schema": {"type": "object", "properties": {"code": {"type": "integer"}}}}
                  ]
                }
                """;

        var result = service.merge(existing, incomingRequest, incomingResponse);

        assertTrue(result.getRequestConfig().contains("\"pageNum\""));
        assertTrue(result.getTestValueConfig().contains("13800000001"));
        assertFalse(result.getRequestConfig().contains("13800000001"));
        assertTrue(result.getMergeSummary().getQueryParamsAdded().contains("pageNum"));
        assertTrue(result.getMergeSummary().isSchemaUpdated());
    }

    /**
     * 前提：同 response id，本地含用户 example，上传包更新 schema。
     * 期望：example 保留于合并结果，summary.userPreserved 含 responseExample。
     */
    @Test
    @Order(2)
    @DisplayName("合并：同 response id 时保留用户 example")
    void merge_preservesResponseExampleWhenIdMatches() {
        String responseId = "resp-abc123";
        TestProjectApi existing = TestProjectApi.builder()
                .requestConfig("""
                        {"configVersion":1,"method":"GET","queryParams":[],"pathParams":[],
                         "declaredHeaders":[],"body":{"mode":"none"}}
                        """)
                .responseConfig(String.format("""
                        {
                          "configVersion": 1,
                          "responses": [
                            {"id": "%s", "name": "成功", "httpStatus": 200, "contentType": "json",
                             "schema": {"type":"object"},
                             "example": {"code": 14, "msg": "custom"}}
                          ]
                        }
                        """, responseId))
                .build();

        String incomingResponse = String.format("""
                {
                  "configVersion": 1,
                  "responses": [
                    {"id": "%s", "name": "成功", "httpStatus": 200, "contentType": "json",
                     "schema": {"type": "object", "properties": {"code": {"type": "integer"}}}}
                  ]
                }
                """, responseId);

        var result = service.merge(existing, existing.getRequestConfig(), incomingResponse);

        assertTrue(result.getResponseConfig().contains("\"code\":14")
                || result.getTestValueConfig().contains("\"code\":14"));
        assertTrue(result.getMergeSummary().getUserPreserved().stream()
                .anyMatch(s -> s.startsWith("responseExample")));
    }

    /**
     * 前提：本地含 legacyKey 参数，上传包 queryParams 为空。
     * 期望：summary 记录 queryParamsRemoved，默认值保留于 test_value_config。
     */
    @Test
    @Order(3)
    @DisplayName("合并：记录已删除 query 参数并保留默认值")
    void merge_tracksRemovedQueryParams() {
        TestProjectApi existing = TestProjectApi.builder()
                .requestConfig("""
                        {
                          "configVersion": 1,
                          "method": "GET",
                          "queryParams": [
                            {"name": "legacyKey", "type": "string", "value": "old-val"}
                          ],
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
                .build();

        String incomingRequest = """
                {
                  "configVersion": 1,
                  "method": "GET",
                  "queryParams": [],
                  "pathParams": [],
                  "declaredHeaders": [],
                  "body": {"mode": "none", "json": {"schema": null, "example": null}}
                }
                """;
        String incomingResponse = """
                {"configVersion":1,"responses":[
                  {"id":"r1","name":"成功","httpStatus":200,"contentType":"json","schema":null}
                ]}
                """;

        var result = service.merge(existing, incomingRequest, incomingResponse);

        assertTrue(result.getMergeSummary().getQueryParamsRemoved().contains("legacyKey"));
        assertTrue(result.getTestValueConfig().contains("legacyKey"));
        assertFalse(result.getRequestConfig().contains("legacyKey"));
    }

    /**
     * 前提：本地 body.json 含 example 字符串。
     * 期望：example 迁入 test_value_config.bodyExample，结构层不含 example。
     */
    @Test
    @Order(4)
    @DisplayName("合并：body example 迁入 test_value_config")
    void merge_migratesBodyExampleToTestValueConfig() {
        TestProjectApi existing = TestProjectApi.builder()
                .requestConfig("""
                        {
                          "configVersion": 1,
                          "method": "POST",
                          "queryParams": [],
                          "pathParams": [],
                          "declaredHeaders": [],
                          "body": {
                            "mode": "json",
                            "json": {
                              "schema": {"type": "object"},
                              "example": "{\\"user\\":\\"admin\\"}"
                            }
                          }
                        }
                        """)
                .responseConfig("""
                        {"configVersion":1,"responses":[
                          {"id":"r1","name":"成功","httpStatus":200,"contentType":"json","schema":null}
                        ]}
                        """)
                .build();

        String incomingRequest = """
                {
                  "configVersion": 1,
                  "method": "POST",
                  "queryParams": [],
                  "pathParams": [],
                  "declaredHeaders": [],
                  "body": {
                    "mode": "json",
                    "json": {
                      "schema": {"type": "object", "properties": {"user": {"type": "string"}}},
                      "example": null
                    }
                  }
                }
                """;
        String incomingResponse = existing.getResponseConfig();

        var result = service.merge(existing, incomingRequest, incomingResponse);

        assertTrue(result.getTestValueConfig().contains("admin"));
        assertTrue(result.getMergeSummary().getUserPreserved().contains("bodyExample"));
        assertFalse(result.getRequestConfig().contains("\"example\":\"{\\\"user\\\":\\\"admin\\\"}\""));
    }

    /**
     * 前提：同名 mobile 参数类型未变，本地含 pattern/maxLength，上传包含 description。
     * 期望：合并后保留 pattern/maxLength，写入 description。
     */
    @Test
    @Order(5)
    @DisplayName("合并：类型未变时保留参数约束并写入 description")
    void merge_preservesParamConstraintsWhenTypeUnchanged() throws Exception {
        TestProjectApi existing = TestProjectApi.builder()
                .requestConfig("""
                        {
                          "configVersion": 1,
                          "method": "GET",
                          "queryParams": [
                            {"name": "mobile", "type": "string", "pattern": "^1\\\\d{10}$", "maxLength": 11}
                          ],
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
                .build();

        String incomingRequest = """
                {
                  "configVersion": 1,
                  "method": "GET",
                  "queryParams": [
                    {"name": "mobile", "type": "string", "required": false, "description": "手机号"}
                  ],
                  "pathParams": [],
                  "declaredHeaders": [],
                  "body": {"mode": "none", "json": {"schema": null, "example": null}}
                }
                """;

        var result = service.merge(existing, incomingRequest, existing.getResponseConfig());
        JsonNode mobile = findQueryParam(result.getRequestConfig(), "mobile");

        assertEquals("^1\\d{10}$", mobile.get("pattern").asText());
        assertEquals(11, mobile.get("maxLength").asInt());
        assertEquals("手机号", mobile.get("description").asText());
    }

    /**
     * 前提：同名 age 参数本地为 string+pattern，上传包改为 integer。
     * 期望：类型变为 integer，丢弃 string 专属约束，保留 description。
     */
    @Test
    @Order(6)
    @DisplayName("合并：类型变更时丢弃旧类型约束")
    void merge_dropsParamConstraintsWhenTypeChanged() throws Exception {
        TestProjectApi existing = TestProjectApi.builder()
                .requestConfig("""
                        {
                          "configVersion": 1,
                          "method": "GET",
                          "queryParams": [
                            {"name": "age", "type": "string", "pattern": "^\\\\d+$", "minLength": 1}
                          ],
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
                .build();

        String incomingRequest = """
                {
                  "configVersion": 1,
                  "method": "GET",
                  "queryParams": [
                    {"name": "age", "type": "integer", "required": false, "description": "年龄"}
                  ],
                  "pathParams": [],
                  "declaredHeaders": [],
                  "body": {"mode": "none", "json": {"schema": null, "example": null}}
                }
                """;

        var result = service.merge(existing, incomingRequest, existing.getResponseConfig());
        JsonNode age = findQueryParam(result.getRequestConfig(), "age");

        assertEquals("integer", age.get("type").asText());
        assertFalse(age.has("pattern"));
        assertFalse(age.has("minLength"));
        assertEquals("年龄", age.get("description").asText());
    }

    /**
     * 前提：body schema mobile 类型未变，本地含 pattern/maxLength，上传包新增 username。
     * 期望：mobile 约束保留并写入 description，username 属性出现。
     */
    @Test
    @Order(7)
    @DisplayName("合并：body schema soft merge 保留约束并增属性")
    void merge_softMergesBodySchemaConstraints() throws Exception {
        TestProjectApi existing = TestProjectApi.builder()
                .requestConfig("""
                        {
                          "configVersion": 1,
                          "method": "POST",
                          "queryParams": [],
                          "pathParams": [],
                          "declaredHeaders": [],
                          "body": {
                            "mode": "json",
                            "json": {
                              "schema": {
                                "type": "object",
                                "properties": {
                                  "mobile": {"type": "string", "pattern": "^1\\\\d{10}$", "maxLength": 11}
                                }
                              },
                              "example": null
                            }
                          }
                        }
                        """)
                .responseConfig("""
                        {"configVersion":1,"responses":[
                          {"id":"r1","name":"成功","httpStatus":200,"contentType":"json","schema":null}
                        ]}
                        """)
                .build();

        String incomingRequest = """
                {
                  "configVersion": 1,
                  "method": "POST",
                  "queryParams": [],
                  "pathParams": [],
                  "declaredHeaders": [],
                  "body": {
                    "mode": "json",
                    "json": {
                      "schema": {
                        "type": "object",
                        "properties": {
                          "mobile": {"type": "string", "description": "手机号"},
                          "username": {"type": "string"}
                        }
                      },
                      "example": null
                    }
                  }
                }
                """;

        var result = service.merge(existing, incomingRequest, existing.getResponseConfig());
        JsonNode props = mapper.readTree(result.getRequestConfig())
                .path("body").path("json").path("schema").path("properties");

        assertEquals("^1\\d{10}$", props.path("mobile").path("pattern").asText());
        assertEquals(11, props.path("mobile").path("maxLength").asInt());
        assertEquals("手机号", props.path("mobile").path("description").asText());
        assertTrue(props.has("username"));
        assertEquals("string", props.path("username").path("type").asText());
    }

    /**
     * 前提：同 response id，本地 schema 含 code.minimum 与 legacy，上传包删 legacy 增 msg。
     * 期望：code.minimum 保留，legacy 消失，msg 出现。
     */
    @Test
    @Order(8)
    @DisplayName("合并：response schema soft merge 并删除已移除属性")
    void merge_softMergesResponseSchemaAndDropsRemovedProperty() throws Exception {
        String responseId = "resp-soft";
        TestProjectApi existing = TestProjectApi.builder()
                .requestConfig("""
                        {"configVersion":1,"method":"GET","queryParams":[],"pathParams":[],
                         "declaredHeaders":[],"body":{"mode":"none"}}
                        """)
                .responseConfig(String.format("""
                        {
                          "configVersion": 1,
                          "responses": [
                            {
                              "id": "%s",
                              "name": "成功",
                              "httpStatus": 200,
                              "contentType": "json",
                              "schema": {
                                "type": "object",
                                "properties": {
                                  "code": {"type": "integer", "minimum": 0},
                                  "legacy": {"type": "string"}
                                }
                              }
                            }
                          ]
                        }
                        """, responseId))
                .build();

        String incomingResponse = String.format("""
                {
                  "configVersion": 1,
                  "responses": [
                    {
                      "id": "%s",
                      "name": "成功",
                      "httpStatus": 200,
                      "contentType": "json",
                      "schema": {
                        "type": "object",
                        "properties": {
                          "code": {"type": "integer", "description": "业务码"},
                          "msg": {"type": "string"}
                        }
                      }
                    }
                  ]
                }
                """, responseId);

        var result = service.merge(existing, existing.getRequestConfig(), incomingResponse);
        JsonNode props = mapper.readTree(result.getResponseConfig())
                .path("responses").get(0).path("schema").path("properties");

        assertEquals(0, props.path("code").path("minimum").asInt());
        assertEquals("业务码", props.path("code").path("description").asText());
        assertTrue(props.has("msg"));
        assertFalse(props.has("legacy"));
    }

    private JsonNode findQueryParam(String requestConfigJson, String name) throws Exception {
        JsonNode arr = mapper.readTree(requestConfigJson).path("queryParams");
        for (JsonNode node : arr) {
            if (name.equals(node.path("name").asText())) {
                return node;
            }
        }
        throw new AssertionError("query param not found: " + name);
    }
}
