package com.qualitest.flow.validate;

import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测必填测值门禁：成功路径缺必填时硬拦。
 * 无 bodyExample 视为空对象；接口测值与节点测值都计入；数字 0 和 false 算已填；空数组算缺；关掉业务码校验时不拦。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class HttpRequiredParamGateTest {

    /** JSON body，schema 要求 name 必填；资产 example 里 name 是空串。 */
    private static final String JSON_NAME_REQUIRED = """
            {"configVersion":1,"method":"POST","queryParams":[],"pathParams":[],"declaredHeaders":[],
             "body":{"mode":"json","json":{"schema":{"type":"object","required":["name"],
             "properties":{"name":{"type":"string"},"count":{"type":"integer"}}},"example":{"name":"","count":0}}}}
            """;

    /** JSON body，schema 要求 items 数组必填。 */
    private static final String JSON_ITEMS_REQUIRED = """
            {"configVersion":1,"method":"POST","queryParams":[],"pathParams":[],"declaredHeaders":[],
             "body":{"mode":"json","json":{"schema":{"type":"object","required":["items"],
             "properties":{"items":{"type":"array","items":{"type":"object"}}}},"example":null}}}
            """;

    /** GET 查询参数 page 标了 required。 */
    private static final String QUERY_PAGE_REQUIRED = """
            {"configVersion":1,"method":"GET","queryParams":[{"name":"page","required":true,"type":"integer"}],
             "pathParams":[],"declaredHeaders":[],"body":{"mode":"none"}}
            """;

    /**
     * 前提：schema required 含 name，节点没写 bodyExample。
     * 期望：硬拦，文案含 body 字段 name。
     */
    @Test
    @Order(1)
    @DisplayName("无节点 body 时缺必填硬拦")
    void missingBodyExample_failsRequired() {
        GraphJson graph = GraphJson.builder().nodes(List.of(httpNode("apply", 1L, null))).build();

        List<String> errors = HttpRequiredParamGate.validate(graph, id -> api(id, JSON_NAME_REQUIRED));

        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("name"));
        assertTrue(errors.get(0).contains("不要改接口资产的 required"));
    }

    /**
     * 前提：节点 bodyExample 显式写 name=0（数字）。
     * 期望：视为已填，通过。
     */
    @Test
    @Order(2)
    @DisplayName("数字 0 算已填")
    void numericZero_countsAsFilled() {
        GraphNode node = httpNode("n", 1L, Map.of("bodyExample", Map.of("name", 0)));
        GraphJson graph = GraphJson.builder().nodes(List.of(node)).build();

        assertTrue(HttpRequiredParamGate.validate(graph, id -> api(id, JSON_NAME_REQUIRED)).isEmpty());
    }

    /**
     * 前提：节点 bodyExample 写 items=[]。
     * 期望：空数组视为未填。
     */
    @Test
    @Order(3)
    @DisplayName("空数组算缺必填")
    void emptyArray_countsAsMissing() {
        GraphNode node = httpNode("n", 1L, Map.of("bodyExample", Map.of("items", List.of())));
        GraphJson graph = GraphJson.builder().nodes(List.of(node)).build();

        List<String> errors = HttpRequiredParamGate.validate(graph, id -> api(id, JSON_ITEMS_REQUIRED));
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("items"));
    }

    /**
     * 前提：缺必填，但节点 successCheck.mode=off。
     * 期望：不拦。
     */
    @Test
    @Order(4)
    @DisplayName("失败路径 mode=off 不拦")
    void successCheckOff_skips() {
        GraphNode node = httpNode("n", 1L, null);
        node.getData().put("successCheck", Map.of("mode", "off"));
        GraphJson graph = GraphJson.builder().nodes(List.of(node)).build();

        assertTrue(HttpRequiredParamGate.validate(graph, id -> api(id, JSON_NAME_REQUIRED)).isEmpty());
    }

    /**
     * 前提：必填 query page，节点未写 paramDefaults。
     * 期望：硬拦 page。
     */
    @Test
    @Order(5)
    @DisplayName("缺必填 query 硬拦")
    void missingRequiredQuery_fails() {
        GraphJson graph = GraphJson.builder().nodes(List.of(httpNode("list", 1L, null))).build();

        List<String> errors = HttpRequiredParamGate.validate(graph, id -> api(id, QUERY_PAGE_REQUIRED));
        assertEquals(1, errors.size());
        assertTrue(errors.get(0).contains("query"));
        assertTrue(errors.get(0).contains("page"));
    }

    /**
     * 前提：节点 paramDefaults 写了 page=1。
     * 期望：通过。
     */
    @Test
    @Order(6)
    @DisplayName("节点补上 query 测值通过")
    void queryParamDefault_ok() {
        GraphNode node = httpNode("list", 1L, Map.of("paramDefaults", Map.of("page", 1)));
        GraphJson graph = GraphJson.builder().nodes(List.of(node)).build();

        assertTrue(HttpRequiredParamGate.validate(graph, id -> api(id, QUERY_PAGE_REQUIRED)).isEmpty());
    }

    /**
     * 前提：节点 bodyExample 写 name 字符串。
     * 期望：通过。
     */
    @Test
    @Order(7)
    @DisplayName("节点补上 body 测值通过")
    void bodyExamplePresent_ok() {
        GraphNode node = httpNode("n", 1L, Map.of("bodyExample", Map.of("name", "alice")));
        GraphJson graph = GraphJson.builder().nodes(List.of(node)).build();

        assertTrue(HttpRequiredParamGate.validate(graph, id -> api(id, JSON_NAME_REQUIRED)).isEmpty());
    }

    /**
     * 前提：接口 TV bodyExample 含 name=alice，节点无 overrides。
     * 期望：通过（issued 叠接口测值）。
     */
    @Test
    @Order(8)
    @DisplayName("接口测值补上 body 时通过")
    void apiTestValueBody_ok() {
        GraphJson graph = GraphJson.builder().nodes(List.of(httpNode("n", 1L, null))).build();
        TestProjectApi withTv = TestProjectApi.builder()
                .testProjectApiId(1L)
                .apiPath("/demo")
                .requestConfig(JSON_NAME_REQUIRED)
                .testValueConfig("{\"request\":{\"bodyExample\":{\"name\":\"alice\"}}}")
                .build();

        assertTrue(HttpRequiredParamGate.validate(graph, id -> withTv).isEmpty());
    }

    /**
     * 前提：节点 bodyExample 写 enabled=false。
     * 期望：布尔 false 算已填。
     */
    @Test
    @Order(9)
    @DisplayName("布尔 false 算已填")
    void booleanFalse_countsAsFilled() {
        String schema = """
                {"configVersion":1,"method":"POST","queryParams":[],"pathParams":[],"declaredHeaders":[],
                 "body":{"mode":"json","json":{"schema":{"type":"object","required":["enabled"],
                 "properties":{"enabled":{"type":"boolean"}}}}}}
                """;
        GraphNode node = httpNode("n", 1L, Map.of("bodyExample", Map.of("enabled", false)));
        GraphJson graph = GraphJson.builder().nodes(List.of(node)).build();

        assertTrue(HttpRequiredParamGate.validate(graph, id -> api(id, schema)).isEmpty());
    }

    /**
     * 前提：必填 query classroomId；节点扁平写 requestValueOverrides.classroomId={{flow.x}}。
     * 期望：叠层分桶后通过（含运行时引用）。
     */
    @Test
    @Order(10)
    @DisplayName("扁平 query 覆盖与 {{flow.*}} 通过")
    void flatQueryOverride_withFlowRef_ok() {
        String schema = """
                {"configVersion":1,"method":"GET",
                 "queryParams":[{"name":"classroomId","required":true,"type":"string"}],
                 "pathParams":[],"declaredHeaders":[],"body":{"mode":"none"}}
                """;
        GraphNode node = httpNode("detail", 1L, Map.of("classroomId", "{{flow.classroomId}}"));
        GraphJson graph = GraphJson.builder().nodes(List.of(node)).build();

        assertTrue(HttpRequiredParamGate.validate(graph, id -> api(id, schema)).isEmpty());
    }

    /**
     * 前提：必填 query page；历史上误把 page 写进 bodyExample。
     * 期望：叠层迁回 paramDefaults 后通过。
     */
    @Test
    @Order(11)
    @DisplayName("误存 bodyExample 的 query 测值仍可通过")
    void queryMisplacedInBodyExample_reclaimed_ok() {
        GraphNode node = httpNode("list", 1L, Map.of("bodyExample", Map.of("page", "1")));
        GraphJson graph = GraphJson.builder().nodes(List.of(node)).build();

        assertTrue(HttpRequiredParamGate.validate(graph, id -> api(id, QUERY_PAGE_REQUIRED)).isEmpty());
    }

    /** 组装一个绑定项目接口的 HTTP 节点；overrides 写入 requestValueOverrides。 */
    private static GraphNode httpNode(String id, Long apiId, Map<String, Object> overrides) {
        Map<String, Object> data = new HashMap<>();
        data.put("name", id);
        data.put("callMode", "project");
        data.put("testProjectApiId", String.valueOf(apiId));
        if (overrides != null) {
            data.put("requestValueOverrides", new HashMap<>(overrides));
        }
        return GraphNode.builder().id(id).type("http").data(data).build();
    }

    /** 构造仅含 requestConfig 的接口资产。 */
    private static TestProjectApi api(Long id, String requestConfig) {
        return TestProjectApi.builder()
                .testProjectApiId(id)
                .apiPath("/demo")
                .requestConfig(requestConfig)
                .build();
    }
}
