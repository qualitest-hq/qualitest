package com.qualitest.flow.validate;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.flow.model.GraphJson;
import org.junit.jupiter.api.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static com.qualitest.flow.support.FlowTestSections.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * 测 GraphJsonValidator：开始节点、HTTP 绑定 warning、subflow 规则与 manifest 夹具。
 * 边界：demo-graph 与 graph-validate-cases；存量 begin/end 保留。
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
    void demoGraphValidate_zeroErrors() {
        begin("demoGraphValidate_zeroErrors");
        String json = loadResource("flow/demo-graph.json");
        GraphJson graph = GraphJson.parse(json);
        GraphValidationResult result = validator.validate(graph);
        log("errors=" + result.getErrors().size() + " warnings=" + result.getWarnings().size());
        assertTrue(result.isOk());
        assertTrue(result.getErrors().isEmpty());
        assertTrue(result.getWarnings().isEmpty());
        end("demoGraphValidate_zeroErrors");
    }

    /**
     * 前提：图含多个无入边节点。
     * 期望：1 条 error，消息含「开始节点」。
     */
    @Test
    @Order(2)
    void multiStartNode_producesError() {
        begin("multiStartNode_producesError");
        String json = loadResource("flow/invalid-multi-start.json");
        GraphJson graph = GraphJson.parse(json);
        GraphValidationResult result = validator.validate(graph);
        log("errors=" + result.getErrors().size() + " -> " + result.getErrors());
        assertFalse(result.isOk());
        assertEquals(1, result.getErrors().size());
        assertTrue(result.getErrors().get(0).contains("开始节点"));
        end("multiStartNode_producesError");
    }

    /**
     * 前提：HTTP 节点未绑定 testProjectApiId。
     * 期望：1 条 warning（含 testProjectApiId）；ok 仍为 true。
     */
    @Test
    @Order(3)
    void httpUnbound_producesWarning() {
        begin("httpUnbound_producesWarning");
        String json = loadResource("flow/invalid-http-unbound.json");
        GraphJson graph = GraphJson.parse(json);
        GraphValidationResult result = validator.validate(graph);
        log("warnings=" + result.getWarnings().size() + " -> " + result.getWarnings());
        assertTrue(result.isOk());
        assertEquals(1, result.getWarnings().size());
        assertTrue(result.getWarnings().get(0).contains("testProjectApiId"));
        end("httpUnbound_producesWarning");
    }

    /**
     * 前提：graph-validate-cases.json 各 fixture。
     * 期望：expectErrors / expectWarnings 条数与 manifest 一致。
     */
    @Test
    @Order(4)
    void validateCases_fromManifest() {
        begin("validateCases_fromManifest");
        for (int i = 0; i < validateCases.size(); i++) {
            JSONObject spec = validateCases.getJSONObject(i);
            String id = spec.getString("id");
            String fixture = spec.getString("fixture");
            int expectErrors = spec.getIntValue("expectErrors");
            int expectWarnings = spec.getIntValue("expectWarnings");
            String json = loadResource("flow/" + fixture);
            GraphValidationResult result = validator.validateJson(json);
            log(id + " errors=" + result.getErrors().size() + " warnings=" + result.getWarnings().size());
            assertEquals(expectErrors, result.getErrors().size(), id + " errors");
            assertEquals(expectWarnings, result.getWarnings().size(), id + " warnings");
        }
        end("validateCases_fromManifest");
    }

    /**
     * 前提：demo-graph 调用 validateStartNodes。
     * 期望：恰有一个开始节点，校验通过。
     */
    @Test
    @Order(5)
    void validateStartNodes_demoGraph_ok() {
        begin("validateStartNodes_demoGraph_ok");
        GraphJson graph = GraphJson.parse(loadResource("flow/demo-graph.json"));
        StartNodesValidation result = validator.validateStartNodes(graph);
        log("ok=" + result.isOk() + " ids=" + result.getIds());
        assertTrue(result.isOk());
        assertEquals(1, result.getIds().size());
        end("validateStartNodes_demoGraph_ok");
    }

    /**
     * 前提：多个无入边节点。
     * 期望：validateStartNodes 失败，并返回候选开始节点 id。
     */
    @Test
    @Order(6)
    void validateStartNodes_multiStart_fails() {
        begin("validateStartNodes_multiStart_fails");
        GraphJson graph = GraphJson.parse(loadResource("flow/invalid-multi-start.json"));
        StartNodesValidation result = validator.validateStartNodes(graph);
        log("ok=" + result.isOk() + " message=" + result.getMessage());
        assertFalse(result.isOk());
        assertTrue(result.getMessage().contains("开始节点"));
        assertTrue(result.getIds().size() > 1);
        end("validateStartNodes_multiStart_fails");
    }

    /**
     * 前提：环图（每个节点都有入边）。
     * 期望：失败；消息含「未找到开始节点」；ids 为空。
     */
    @Test
    @Order(7)
    void validateStartNodes_noStart_fails() {
        begin("validateStartNodes_noStart_fails");
        GraphJson graph = GraphJson.parse(loadResource("flow/invalid-no-start.json"));
        StartNodesValidation result = validator.validateStartNodes(graph);
        log("ok=" + result.isOk() + " message=" + result.getMessage());
        assertFalse(result.isOk());
        assertTrue(result.getMessage().contains("未找到开始节点"));
        assertTrue(result.getIds().isEmpty());
        end("validateStartNodes_noStart_fails");
    }

    /**
     * 前提：subflow 缺 subflowId，且未配 inputs/outputs。
     * 期望：error + inputs/outputs 相关 warning。
     */
    @Test
    @Order(8)
    void subflowMissingId_producesErrorAndWarnings() {
        begin("subflowMissingId_producesErrorAndWarnings");
        String json = loadResource("flow/invalid-subflow-missing-id.json");
        GraphValidationResult result = validator.validateJson(json);
        log("errors=" + result.getErrors().size() + " warnings=" + result.getWarnings().size());
        assertFalse(result.isOk());
        assertEquals(1, result.getErrors().size());
        assertTrue(result.getErrors().get(0).contains("subflowId"));
        assertEquals(2, result.getWarnings().size());
        end("subflowMissingId_producesErrorAndWarnings");
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
