package com.qualitest.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.qualitest.project.domain.TestProjectApi;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static com.qualitest.flow.support.FlowTestSections.begin;
import static com.qualitest.flow.support.FlowTestSections.end;
import static com.qualitest.flow.support.FlowTestSections.log;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 结构合并单元测试。
 * <p>
 * 覆盖：参数默认值迁入测试值层、响应 example 保留、删除参数归档、body 示例迁出结构层、
 * 字段级 soft merge（约束保留 / 类型变更丢约束 / schema 递归）。
 * <p>
 * 运行（qualitest 目录）：mvn test -pl qualitest-system -am -DskipTests=false -Dtest=ApiImportMergeServiceTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ApiImportMergeServiceTest {

    private final ApiImportMergeService service = new ApiImportMergeService();
    private final ObjectMapper mapper = new ObjectMapper();

    /**
     * 本地 query 参数 value 应迁入 test_value_config，结构层不含 value；上传包新增参数应记入 queryParamsAdded。
     */
    @Test
    @Order(1)
    void merge_migratesParamDefaultsAndAddsQueryParam() {
        begin("merge_migratesParamDefaultsAndAddsQueryParam");
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
        log("paramDefault migrated; pageNum added");
        end("merge_migratesParamDefaultsAndAddsQueryParam");
    }

    /**
     * 同一 response id 下用户 example 应保留：写入 test_value_config 或合并后的 response_config，并记入 userPreserved。
     */
    @Test
    @Order(2)
    void merge_preservesResponseExampleWhenIdMatches() {
        begin("merge_preservesResponseExampleWhenIdMatches");
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
        log("response example preserved for id=" + responseId);
        end("merge_preservesResponseExampleWhenIdMatches");
    }

    /**
     * 代码侧移除的 query 参数应记入 queryParamsRemoved，默认值仍保留在 test_value_config.removedParams。
     */
    @Test
    @Order(3)
    void merge_tracksRemovedQueryParams() {
        begin("merge_tracksRemovedQueryParams");
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
        log("legacyKey removed from structure, tracked in summary");
        end("merge_tracksRemovedQueryParams");
    }

    /**
     * body.json.example 应迁入 test_value_config.request.bodyExample，结构层去掉 example。
     */
    @Test
    @Order(4)
    void merge_migratesBodyExampleToTestValueConfig() {
        begin("merge_migratesBodyExampleToTestValueConfig");
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
        log("bodyExample migrated to test_value_config");
        end("merge_migratesBodyExampleToTestValueConfig");
    }

    /**
     * 同名参数类型未变时，本地 pattern/maxLength 应保留，上传包 description 仍可写入。
     */
    @Test
    @Order(5)
    void merge_preservesParamConstraintsWhenTypeUnchanged() throws Exception {
        begin("merge_preservesParamConstraintsWhenTypeUnchanged");
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
        log("mobile pattern/maxLength preserved; description from incoming");
        end("merge_preservesParamConstraintsWhenTypeUnchanged");
    }

    /**
     * 同名参数类型变更时，以上传包为准，丢弃仅适用于旧类型的 pattern。
     */
    @Test
    @Order(6)
    void merge_dropsParamConstraintsWhenTypeChanged() throws Exception {
        begin("merge_dropsParamConstraintsWhenTypeChanged");
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
        log("age type changed to integer; string constraints dropped");
        end("merge_dropsParamConstraintsWhenTypeChanged");
    }

    /**
     * Body schema 递归 soft merge：同名未变字段保留 pattern，上传包新增属性应出现。
     */
    @Test
    @Order(7)
    void merge_softMergesBodySchemaConstraints() throws Exception {
        begin("merge_softMergesBodySchemaConstraints");
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
        log("body schema mobile constraints preserved; username added");
        end("merge_softMergesBodySchemaConstraints");
    }

    /**
     * 同 response id 下 schema soft merge：未变字段保留约束，上传包删除的属性应从结构消失。
     */
    @Test
    @Order(8)
    void merge_softMergesResponseSchemaAndDropsRemovedProperty() throws Exception {
        begin("merge_softMergesResponseSchemaAndDropsRemovedProperty");
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
        log("response schema code.minimum preserved; legacy removed; msg added");
        end("merge_softMergesResponseSchemaAndDropsRemovedProperty");
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
