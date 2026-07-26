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
 * {@link GraphJsonValidator} 单元测试：验证流程图 JSON 的结构与业务规则校验。
 * <p>
 * 被测对象检查：唯一开始节点、HTTP 节点 API 绑定、condition 分支完整性等，
 * 返回 {@link GraphValidationResult}（errors 阻断运行，warnings 仅提示）。
 * 测试数据来自 demo-graph 及 {@code graph-validate-cases.json} manifest。
 * <p>
 * 运行（qualitest 目录）：mvn test -pl qualitest-system -am -DskipTests=false -Dtest=GraphJsonValidatorTest
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
     * 标准 demo-graph.json 结构完整、规则合规。
     * 期望：validate 返回 ok，errors 和 warnings 均为空。
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
     * 图中存在多个无入边节点（多个「开始节点」）时校验应失败。
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
     * HTTP 节点未绑定 testProjectApiId 时产生 warning，但不阻断校验（ok 仍为 true）。
     * 期望：1 条 warning，消息含 testProjectApiId。
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
     * 数据驱动：遍历 graph-validate-cases.json manifest，对每个 fixture 断言
     * expectErrors / expectWarnings 条数与 manifest 声明一致。
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
     * {@link GraphJsonValidator#validateStartNodes} 子校验：demo-graph 恰有一个开始节点。
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
     * 多个无入边节点时 validateStartNodes 应失败，返回所有候选开始节点 id。
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
     * 环图（每个节点都有入边，无开始节点）时 validateStartNodes 应失败。
     * 期望：消息含「未找到开始节点」，ids 为空。
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
     * subflow 节点缺少 subflowId 时应产生 error；未配置 inputs/outputs 时产生 warning。
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
