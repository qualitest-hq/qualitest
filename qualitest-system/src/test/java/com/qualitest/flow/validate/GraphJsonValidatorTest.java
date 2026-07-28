package com.qualitest.flow.validate;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.flow.model.GraphJson;
import org.junit.jupiter.api.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测 GraphJsonValidator：开始节点、HTTP 绑定 warning、subflow 规则与 manifest 夹具。
 * 边界：demo-graph 与 graph-validate-cases；无 DB。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=GraphJsonValidatorTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class GraphJsonValidatorTest {

    private GraphJsonValidator validator;

    private JSONArray validateCases;

    @BeforeEach
    void setUp() {
        validator = new GraphJsonValidator();
        String casesJson = loadResource("flow/graph-validate-cases.json");
        validateCases = JSON.parseObject(casesJson).getJSONArray("cases");
    }

    /**
     * 前提：标准 demo-graph.json。
     * 期望：ok；errors/warnings 为空。
     */
    @Test
    @Order(1)
    @DisplayName("demo-graph 校验无错误警告")
    void demoGraphValidate_zeroErrors() {
        String json = loadResource("flow/demo-graph.json");
        GraphJson graph = GraphJson.parse(json);
        GraphValidationResult result = validator.validate(graph);
        assertTrue(result.isOk());
        assertTrue(result.getErrors().isEmpty());
        assertTrue(result.getWarnings().isEmpty());
    }

    /**
     * 前提：图含多个无入边节点。
     * 期望：1 条 error，消息含「开始节点」。
     */
    @Test
    @Order(2)
    @DisplayName("多开始节点产生错误")
    void multiStartNode_producesError() {
        String json = loadResource("flow/invalid-multi-start.json");
        GraphJson graph = GraphJson.parse(json);
        GraphValidationResult result = validator.validate(graph);
        assertFalse(result.isOk());
        assertEquals(1, result.getErrors().size());
        assertTrue(result.getErrors().get(0).contains("开始节点"));
    }

    /**
     * 前提：HTTP 节点未绑定 testProjectApiId。
     * 期望：1 条 warning（含 testProjectApiId）；ok 仍为 true。
     */
    @Test
    @Order(3)
    @DisplayName("未绑定 HTTP 产生 warning")
    void httpUnbound_producesWarning() {
        String json = loadResource("flow/invalid-http-unbound.json");
        GraphJson graph = GraphJson.parse(json);
        GraphValidationResult result = validator.validate(graph);
        assertTrue(result.isOk());
        assertEquals(1, result.getWarnings().size());
        assertTrue(result.getWarnings().get(0).contains("testProjectApiId"));
    }

    /**
     * 前提：graph-validate-cases.json 各 fixture。
     * 期望：expectErrors / expectWarnings 条数与 manifest 一致。
     */
    @Test
    @Order(4)
    @DisplayName("manifest 夹具错误警告条数一致")
    void validateCases_fromManifest() {
        for (int i = 0; i < validateCases.size(); i++) {
            JSONObject spec = validateCases.getJSONObject(i);
            String id = spec.getString("id");
            String fixture = spec.getString("fixture");
            int expectErrors = spec.getIntValue("expectErrors");
            int expectWarnings = spec.getIntValue("expectWarnings");
            String json = loadResource("flow/" + fixture);
            GraphValidationResult result = validator.validateJson(json);
            assertEquals(expectErrors, result.getErrors().size(), id + " errors");
            assertEquals(expectWarnings, result.getWarnings().size(), id + " warnings");
        }
    }

    /**
     * 前提：demo-graph 调用 validateStartNodes。
     * 期望：恰有一个开始节点，校验通过。
     */
    @Test
    @Order(5)
    @DisplayName("demo-graph 开始节点校验通过")
    void validateStartNodes_demoGraph_ok() {
        GraphJson graph = GraphJson.parse(loadResource("flow/demo-graph.json"));
        StartNodesValidation result = validator.validateStartNodes(graph);
        assertTrue(result.isOk());
        assertEquals(1, result.getIds().size());
    }

    /**
     * 前提：多个无入边节点。
     * 期望：validateStartNodes 失败，并返回候选开始节点 id。
     */
    @Test
    @Order(6)
    @DisplayName("多开始节点 validateStartNodes 失败")
    void validateStartNodes_multiStart_fails() {
        GraphJson graph = GraphJson.parse(loadResource("flow/invalid-multi-start.json"));
        StartNodesValidation result = validator.validateStartNodes(graph);
        assertFalse(result.isOk());
        assertTrue(result.getMessage().contains("开始节点"));
        assertTrue(result.getIds().size() > 1);
    }

    /**
     * 前提：环图（每个节点都有入边）。
     * 期望：失败；消息含「未找到开始节点」；ids 为空。
     */
    @Test
    @Order(7)
    @DisplayName("环图未找到开始节点")
    void validateStartNodes_noStart_fails() {
        GraphJson graph = GraphJson.parse(loadResource("flow/invalid-no-start.json"));
        StartNodesValidation result = validator.validateStartNodes(graph);
        assertFalse(result.isOk());
        assertTrue(result.getMessage().contains("未找到开始节点"));
        assertTrue(result.getIds().isEmpty());
    }

    /**
     * 前提：subflow 缺 subflowId，且未配 inputs/outputs。
     * 期望：error + inputs/outputs 相关 warning。
     */
    @Test
    @Order(8)
    @DisplayName("subflow 缺 id 产生 error 与 warning")
    void subflowMissingId_producesErrorAndWarnings() {
        String json = loadResource("flow/invalid-subflow-missing-id.json");
        GraphValidationResult result = validator.validateJson(json);
        assertFalse(result.isOk());
        assertEquals(1, result.getErrors().size());
        assertTrue(result.getErrors().get(0).contains("subflowId"));
        assertEquals(2, result.getWarnings().size());
    }

    private static String loadResource(String path) {
        InputStream in = GraphJsonValidatorTest.class.getClassLoader().getResourceAsStream(path);
        assertNotNull(in, "missing resource: " + path);
        try (InputStream stream = in) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
