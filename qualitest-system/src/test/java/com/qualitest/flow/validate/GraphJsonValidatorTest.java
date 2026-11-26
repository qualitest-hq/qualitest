package com.qualitest.flow.validate;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import org.junit.jupiter.api.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 测 GraphJsonValidator：开始节点、HTTP 绑定、persistMinimal、manifest 夹具，
 * 以及 alwaysRun / snapshotBefore 的布尔类型与节点类型白名单。
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
        assertFalse(result.getErrors().isEmpty());
        assertTrue(result.getErrors().stream().anyMatch(e ->
                e.contains("开始") || e.contains("入口") || e.contains("起点")));
    }

    /**
     * 前提：HTTP 节点未绑定 testProjectApiId。
     * 期望：结构校验 ok，且 warnings 不含 testProjectApiId 文案。
     */
    @Test
    @Order(3)
    @DisplayName("未绑定 HTTP 不由结构校验告警")
    void httpUnbound_noStructureWarning() {
        String json = loadResource("flow/invalid-http-unbound.json");
        GraphJson graph = GraphJson.parse(json);
        GraphValidationResult result = validator.validate(graph);
        assertTrue(result.isOk());
        assertTrue(result.getWarnings().stream().noneMatch(w -> w.contains("testProjectApiId")));
    }

    /**
     * 前提：图仅缺 callMode（完整校验会 error）。
     * 期望：persistMinimal 地板通过。
     */
    @Test
    @Order(4)
    @DisplayName("persistMinimal 允许字段级半成品")
    void persistMinimal_allowsFieldLevelDraft() {
        GraphNode http = GraphNode.builder()
                .id("n1")
                .type("http")
                .name("半成品")
                .data(new java.util.HashMap<>(java.util.Map.of("callMode", "project")))
                .build();
        GraphJson graph = GraphJson.builder()
                .nodes(java.util.List.of(http))
                .edges(java.util.List.of())
                .build();
        GraphValidationResult full = validator.validate(graph);
        assertFalse(full.isOk());
        GraphValidationResult floor = validator.validate(graph, GraphValidationOptions.persistMinimal());
        assertTrue(floor.isOk());
    }

    /**
     * 前提：graph-validate-cases.json 各 fixture。
     * 期望：expectErrors / expectWarnings 条数与 manifest 一致。
     */
    @Test
    @Order(5)
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
    @Order(15)
    @DisplayName("demo-graph 开始节点校验通过")
    void validateStartNodes_demoGraph_ok() {
        GraphJson graph = GraphJson.parse(loadResource("flow/demo-graph.json"));
        StartNodesValidation result = validator.validateStartNodes(graph);
        assertTrue(result.isOk());
        assertEquals(1, result.getIds().size());
    }

    /**
     * 前提：有入口 + 非入口无入边节点。
     * 期望：validateStartNodes 失败（仅入口可作开始），并返回候选无入边 id。
     */
    @Test
    @Order(6)
    @DisplayName("多开始节点 validateStartNodes 失败")
    void validateStartNodes_multiStart_fails() {
        GraphJson graph = GraphJson.parse(loadResource("flow/invalid-multi-start.json"));
        StartNodesValidation result = validator.validateStartNodes(graph);
        assertFalse(result.isOk());
        assertTrue(result.getMessage().contains("非入口的无入边"));
        assertTrue(result.getIds().size() > 1);
    }

    /**
     * 前提：环图（每个节点都有入边，且无入口）。
     * 期望：失败；消息含「须至少配置一个入口」；ids 为空。
     */
    @Test
    @Order(7)
    @DisplayName("环图无入口 validateStartNodes 失败")
    void validateStartNodes_noStart_fails() {
        GraphJson graph = GraphJson.parse(loadResource("flow/invalid-no-start.json"));
        StartNodesValidation result = validator.validateStartNodes(graph);
        assertFalse(result.isOk());
        assertTrue(result.getMessage().contains("须至少配置一个入口"));
        assertTrue(result.getIds().isEmpty());
    }

    /**
     * 前提：subflow 缺 subflowId，且未配 inputs。
     * 期望：仅 subflowId error；空 inputs 不再告警。
     */
    @Test
    @Order(8)
    @DisplayName("subflow 缺 id 产生 error，空 IO 无 warning")
    void subflowMissingId_producesErrorAndWarnings() {
        String json = loadResource("flow/invalid-subflow-missing-id.json");
        GraphValidationResult result = validator.validateJson(json);
        assertFalse(result.isOk());
        assertEquals(1, result.getErrors().size());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("subflowId")));
        assertEquals(0, result.getWarnings().size());
    }

    @Test
    @Order(9)
    @DisplayName("assert 空 left / 非法 http.body.$ 产生 error")
    void assertBadLeft_producesError() {
        String json = """
                {
                  "nodes": [
                    {"id":"1","type":"http","name":"h","position":{"x":0,"y":0},"data":{"callMode":"external","externalUrl":"https://e.example/a","httpMethod":"GET"}},
                    {"id":"2","type":"assert","name":"a","position":{"x":100,"y":0},"data":{"rules":[{"left":"","operator":"eq","right":"0"}]}}
                  ],
                  "edges":[{"id":"e1","source":"1","target":"2"}]
                }
                """;
        GraphValidationResult emptyLeft = validator.validateJson(json);
        assertFalse(emptyLeft.isOk());
        assertTrue(emptyLeft.getErrors().stream().anyMatch(e -> e.contains("left 不能为空")));

        String json2 = """
                {
                  "nodes": [
                    {"id":"1","type":"http","name":"h","position":{"x":0,"y":0},"data":{"callMode":"external","externalUrl":"https://e.example/a","httpMethod":"GET"}},
                    {"id":"2","type":"assert","name":"a","position":{"x":100,"y":0},"data":{"rules":[{"left":"http.body.$.data.code","operator":"eq","right":"0"}]}}
                  ],
                  "edges":[{"id":"e1","source":"1","target":"2"}]
                }
                """;
        GraphValidationResult badPath = validator.validateJson(json2);
        assertFalse(badPath.isOk());
        assertTrue(badPath.getErrors().stream().anyMatch(e -> e.contains("http.body.$.…")));
    }

    @Test
    @Order(10)
    @DisplayName("http extract 坏 JsonPath 产生 error")
    void httpExtractBadJsonPath_producesError() {
        String json = """
                {
                  "nodes": [
                    {"id":"1","type":"http","name":"h","position":{"x":0,"y":0},"data":{
                      "callMode":"external","externalUrl":"https://e.example/a","httpMethod":"GET",
                      "extracts":[{"from":"body","expr":"$.data[","scope":"flow","name":"x"}]
                    }}
                  ],
                  "edges":[{"id":"e0","source":"e1","target":"1"}]
                }
                """;
        GraphValidationResult result = validator.validateJson(json);
        assertFalse(result.isOk());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("JsonPath 无法解析")));
    }

    @Test
    @Order(16)
    @DisplayName("http extract 过滤器后紧跟 [n] 产生 warning 不硬拦")
    void httpExtractFilterThenIndex_producesWarning() {
        // 前提：合法 JsonPath，但过滤器后写了 [0]
        // 期望：ok=true；warnings 提示去掉 [n]
        String json = """
                {
                  "meta":{},
                  "nodes": [
                    {"id":"e1","type":"scenario-entry","name":"入口","position":{"x":0,"y":0},"data":{"scenarios":[{"id":"s1","name":"默认","envId":"","inSeed":{}}]}},
                    {"id":"1","type":"http","name":"h","position":{"x":0,"y":0},"data":{
                      "callMode":"external","externalUrl":"https://e.example/a","httpMethod":"GET",
                      "extracts":[{"from":"body","expr":"$.data.rows[?(@.couponId==3002)][0].couponId","scope":"flow","name":"couponId"}]
                    }}
                  ],
                  "edges":[{"id":"e0","source":"e1","target":"1"}]
                }
                """;
        GraphValidationResult result = validator.validateJson(json);
        assertTrue(result.isOk());
        assertTrue(result.getWarnings().stream().anyMatch(w ->
                w.contains("过滤器后的 [n]") && w.contains("去掉 [n]")));
    }

    @Test
    @Order(11)
    @DisplayName("assert 空 rules / condition 空 branches / assign 空 / delay 超限 为 error")
    void hardenedNodeFields_produceErrors() {
        String emptyAssert = """
                {
                  "meta":{},
                  "nodes": [
                    {"id":"e1","type":"scenario-entry","name":"入口","position":{"x":0,"y":0},"data":{"scenarios":[{"id":"s1","name":"默认","envId":"","inSeed":{}}]}},
                    {"id":"1","type":"assert","name":"a","position":{"x":0,"y":0},"data":{"rules":[]}}
                  ],
                  "edges":[{"id":"e0","source":"e1","target":"1"}]
                }
                """;
        GraphValidationResult a = validator.validateJson(emptyAssert);
        assertTrue(a.getErrors().stream().anyMatch(e -> e.contains("rules 不能为空")));

        String emptyCond = """
                {
                  "meta":{},
                  "nodes": [
                    {"id":"e1","type":"scenario-entry","name":"入口","position":{"x":0,"y":0},"data":{"scenarios":[{"id":"s1","name":"默认","envId":"","inSeed":{}}]}},
                    {"id":"1","type":"condition","name":"c","position":{"x":0,"y":0},"data":{"branches":[]}}
                  ],
                  "edges":[{"id":"e0","source":"e1","target":"1"}]
                }
                """;
        GraphValidationResult c = validator.validateJson(emptyCond);
        assertTrue(c.getErrors().stream().anyMatch(e -> e.contains("缺少 branches")));

        String emptyAssign = """
                {
                  "meta":{},
                  "nodes": [
                    {"id":"e1","type":"scenario-entry","name":"入口","position":{"x":0,"y":0},"data":{"scenarios":[{"id":"s1","name":"默认","envId":"","inSeed":{}}]}},
                    {"id":"1","type":"assign","name":"as","position":{"x":0,"y":0},"data":{"assignments":[]}}
                  ],
                  "edges":[{"id":"e0","source":"e1","target":"1"}]
                }
                """;
        GraphValidationResult as = validator.validateJson(emptyAssign);
        assertTrue(as.getErrors().stream().anyMatch(e -> e.contains("assignments 不能为空")));

        String delayOver = """
                {
                  "meta":{},
                  "nodes": [
                    {"id":"e1","type":"scenario-entry","name":"入口","position":{"x":0,"y":0},"data":{"scenarios":[{"id":"s1","name":"默认","envId":"","inSeed":{}}]}},
                    {"id":"1","type":"delay","name":"d","position":{"x":0,"y":0},"data":{"ms":70000}}
                  ],
                  "edges":[{"id":"e0","source":"e1","target":"1"}]
                }
                """;
        GraphValidationResult d = validator.validateJson(delayOver);
        assertTrue(d.getErrors().stream().anyMatch(e -> e.contains("ms 超过上限")));

        String emptyInput = """
                {
                  "meta":{},
                  "nodes": [
                    {"id":"e1","type":"scenario-entry","name":"入口","position":{"x":0,"y":0},"data":{"scenarios":[{"id":"s1","name":"默认","envId":"","inSeed":{}}]}},
                    {"id":"1","type":"input","name":"in","position":{"x":0,"y":0},"data":{"fields":[]}}
                  ],
                  "edges":[{"id":"e0","source":"e1","target":"1"}]
                }
                """;
        GraphValidationResult in = validator.validateJson(emptyInput);
        assertTrue(in.getErrors().stream().anyMatch(e -> e.contains("fields 不能为空")));

        String badInputType = """
                {
                  "meta":{},
                  "nodes": [
                    {"id":"e1","type":"scenario-entry","name":"入口","position":{"x":0,"y":0},"data":{"scenarios":[{"id":"s1","name":"默认","envId":"","inSeed":{}}]}},
                    {"id":"1","type":"input","name":"in","position":{"x":0,"y":0},"data":{"fields":[
                      {"name":"x","type":"file"}
                    ]}}
                  ],
                  "edges":[{"id":"e0","source":"e1","target":"1"}]
                }
                """;
        GraphValidationResult badType = validator.validateJson(badInputType);
        assertTrue(badType.getErrors().stream().anyMatch(e -> e.contains("type 无效")));
    }

    /**
     * 前提：IF 无 target；ELSE 有 target 且有对应出边。
     * 期望：校验通过。
     * 另测：分支有 target 但无出边 → warnings；有出边但无分支 target → errors。
     */
    @Test
    @Order(13)
    @DisplayName("condition 无 target 合法；有出边无 target 硬拦")
    void conditionEndBranchValidation() {
        String endOk = """
                {
                  "meta":{},
                  "nodes": [
                    {"id":"e1","type":"scenario-entry","name":"入口","position":{"x":0,"y":0},"data":{"scenarios":[{"id":"s1","name":"默认","envId":"","inSeed":{}}]}},
                    {"id":"c1","type":"condition","name":"c","position":{"x":0,"y":0},"data":{"branches":[
                      {"id":"b_if","kind":"if","conditions":[{"left":"flow.x","operator":"eq","right":"1"}]},
                      {"id":"b_else","kind":"else","target":"n2"}
                    ]}},
                    {"id":"n2","type":"http","name":"h","position":{"x":1,"y":0},"data":{"callMode":"external","externalUrl":"https://x","httpMethod":"GET"}}
                  ],
                  "edges":[
                    {"id":"e0","source":"e1","target":"c1"},
                    {"id":"e1","source":"c1","target":"n2"}
                  ]
                }
                """;
        GraphValidationResult ok = validator.validateJson(endOk);
        assertTrue(ok.isOk(), () -> "errors=" + ok.getErrors());
        assertFalse(ok.getWarnings().stream().anyMatch(w -> w.contains("未绑定 target")));

        String danglingTarget = """
                {
                  "meta":{},
                  "nodes": [
                    {"id":"e1","type":"scenario-entry","name":"入口","position":{"x":0,"y":0},"data":{"scenarios":[{"id":"s1","name":"默认","envId":"","inSeed":{}}]}},
                    {"id":"c1","type":"condition","name":"c","position":{"x":0,"y":0},"data":{"branches":[
                      {"id":"b_if","kind":"if","target":"n2","conditions":[{"left":"flow.x","operator":"eq","right":"1"}]},
                      {"id":"b_else","kind":"else","target":"n2"}
                    ]}},
                    {"id":"n2","type":"http","name":"h","position":{"x":1,"y":0},"data":{"callMode":"external","externalUrl":"https://x","httpMethod":"GET"}}
                  ],
                  "edges":[
                    {"id":"e0","source":"e1","target":"c1"}
                  ]
                }
                """;
        GraphValidationResult dangling = validator.validateJson(danglingTarget);
        assertTrue(dangling.getWarnings().stream().anyMatch(w -> w.contains("无对应出边")));

        String orphanEdge = """
                {
                  "meta":{},
                  "nodes": [
                    {"id":"e1","type":"scenario-entry","name":"入口","position":{"x":0,"y":0},"data":{"scenarios":[{"id":"s1","name":"默认","envId":"","inSeed":{}}]}},
                    {"id":"c1","type":"condition","name":"c","position":{"x":0,"y":0},"data":{"branches":[
                      {"id":"b_if","kind":"if","conditions":[{"left":"flow.x","operator":"eq","right":"1"}]},
                      {"id":"b_else","kind":"else"}
                    ]}},
                    {"id":"n2","type":"http","name":"h","position":{"x":1,"y":0},"data":{"callMode":"external","externalUrl":"https://x","httpMethod":"GET"}}
                  ],
                  "edges":[
                    {"id":"e0","source":"e1","target":"c1"},
                    {"id":"e1","source":"c1","target":"n2","label":"else"}
                  ]
                }
                """;
        GraphValidationResult orphan = validator.validateJson(orphanEdge);
        assertFalse(orphan.isOk());
        assertTrue(orphan.getErrors().stream().anyMatch(e -> e.contains("出边无对应 branches.target")));
    }

    @Test
    @Order(13)
    @DisplayName("graph_json 边含 sourceHandle 字段时报错")
    void conditionEdgeSourceHandle_forbiddenInGraphJson() {
        String json = """
                {
                  "meta":{},
                  "nodes": [
                    {"id":"e1","type":"scenario-entry","name":"入口","position":{"x":0,"y":0},"data":{"scenarios":[{"id":"s1","name":"默认","envId":"","inSeed":{}}]}},
                    {"id":"c1","type":"condition","name":"c","position":{"x":0,"y":0},"data":{"branches":[
                      {"id":"b_if","kind":"if","target":"n2","conditions":[{"left":"flow.x","operator":"eq","right":"1"}]}
                    ]}},
                    {"id":"n2","type":"http","name":"h","position":{"x":0,"y":0},"data":{"callMode":"project"}}
                  ],
                  "edges":[
                    {"id":"e1","source":"c1","target":"n2","sourceHandle":"out-b_if"}
                  ]
                }
                """;
        GraphValidationResult result = validator.validateJson(json);
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("sourceHandle")));
    }

    /**
     * 前提：图含多个无入边节点；Staging 分批确认延后拓扑。
     * 期望：ok；errors 空；warnings 含 O7「未确认的连线」文案。
     */
    @Test
    @Order(12)
    @DisplayName("延后拓扑时多开始降为 O7 警告")
    void multiStartNode_deferredAsWarning() {
        String json = loadResource("flow/invalid-multi-start.json");
        GraphJson graph = GraphJson.parse(json);
        GraphValidationResult result = validator.validate(graph, GraphValidationOptions.stagingPartialConfirm());
        assertTrue(result.isOk());
        assertTrue(result.getErrors().isEmpty());
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("未确认的连线")));
        assertTrue(result.getWarnings().stream().anyMatch(w -> w.contains("开始节点")));
    }

    /**
     * 前提：断言 left 分别引用不存在的节点 id、delay 节点 id、合法 HTTP 节点 id。
     * 期望：缺失 id 报「节点不存在」；非 HTTP 报「只能引用 HTTP」；合法引用不因 nodes 目标产生错误。
     */
    @Test
    @Order(17)
    @DisplayName("assert nodes.<id>.http 目标缺失或非 HTTP 报错")
    void assertNodesHttp_missingOrNonHttp_producesError() {
        String missing = """
                {
                  "meta":{},
                  "nodes": [
                    {"id":"e1","type":"scenario-entry","name":"入口","position":{"x":0,"y":0},"data":{"scenarios":[{"id":"s1","name":"默认","envId":"","inSeed":{}}]}},
                    {"id":"1","type":"http","name":"h","position":{"x":0,"y":0},"data":{"callMode":"external","externalUrl":"https://e.example/a","httpMethod":"GET"}},
                    {"id":"2","type":"assert","name":"a","position":{"x":100,"y":0},"data":{"rules":[{"left":"nodes.missing.http.body.code","operator":"eq","right":"0"}]}}
                  ],
                  "edges":[{"id":"e1","source":"1","target":"2"}]
                }
                """;
        GraphValidationResult miss = validator.validateJson(missing);
        assertFalse(miss.isOk());
        assertTrue(miss.getErrors().stream().anyMatch(e -> e.contains("节点不存在") && e.contains("missing")));

        String nonHttp = """
                {
                  "meta":{},
                  "nodes": [
                    {"id":"e1","type":"scenario-entry","name":"入口","position":{"x":0,"y":0},"data":{"scenarios":[{"id":"s1","name":"默认","envId":"","inSeed":{}}]}},
                    {"id":"1","type":"http","name":"h","position":{"x":0,"y":0},"data":{"callMode":"external","externalUrl":"https://e.example/a","httpMethod":"GET"}},
                    {"id":"d1","type":"delay","name":"d","position":{"x":50,"y":0},"data":{"ms":10}},
                    {"id":"2","type":"assert","name":"a","position":{"x":100,"y":0},"data":{"rules":[{"left":"nodes.d1.http.body.code","operator":"eq","right":"0"}]}}
                  ],
                  "edges":[{"id":"e1","source":"1","target":"d1"},{"id":"e2","source":"d1","target":"2"}]
                }
                """;
        GraphValidationResult badType = validator.validateJson(nonHttp);
        assertFalse(badType.isOk());
        assertTrue(badType.getErrors().stream().anyMatch(e -> e.contains("只能引用 HTTP")));

        String ok = """
                {
                  "meta":{},
                  "nodes": [
                    {"id":"e1","type":"scenario-entry","name":"入口","position":{"x":0,"y":0},"data":{"scenarios":[{"id":"s1","name":"默认","envId":"","inSeed":{}}]}},
                    {"id":"1","type":"http","name":"h","position":{"x":0,"y":0},"data":{"callMode":"external","externalUrl":"https://e.example/a","httpMethod":"GET"}},
                    {"id":"2","type":"assert","name":"a","position":{"x":100,"y":0},"data":{"rules":[{"left":"nodes.1.http.body.code","operator":"eq","right":"0"}]}}
                  ],
                  "edges":[{"id":"e1","source":"1","target":"2"}]
                }
                """;
        GraphValidationResult good = validator.validateJson(ok);
        assertTrue(good.getErrors().stream().noneMatch(e -> e.contains("nodes.1")));
    }

    /**
     * 前提：HTTP 节点 alwaysRun 为布尔 true / false / 缺省。
     * 期望：均不因 alwaysRun 报错。
     */
    @Test
    @Order(18)
    @DisplayName("alwaysRun 合法布尔通过")
    void alwaysRun_booleanOk() {
        assertTrue(validator.validateJson(minimalHttpGraph("\"alwaysRun\":true")).isOk());
        assertTrue(validator.validateJson(minimalHttpGraph("\"alwaysRun\":false")).isOk());
        assertTrue(validator.validateJson(minimalHttpGraph("")).isOk());
    }

    /**
     * 前提：HTTP 节点 alwaysRun 为字符串或数字。
     * 期望：error 含「须为 JSON 布尔」。
     */
    @Test
    @Order(19)
    @DisplayName("alwaysRun 字符串或数字报错")
    void alwaysRun_nonBoolean_producesError() {
        GraphValidationResult str = validator.validateJson(minimalHttpGraph("\"alwaysRun\":\"true\""));
        assertFalse(str.isOk());
        assertTrue(str.getErrors().stream().anyMatch(e -> e.contains("alwaysRun") && e.contains("须为 JSON 布尔")));

        GraphValidationResult num = validator.validateJson(minimalHttpGraph("\"alwaysRun\":1"));
        assertFalse(num.isOk());
        assertTrue(num.getErrors().stream().anyMatch(e -> e.contains("alwaysRun") && e.contains("须为 JSON 布尔")));
    }

    /**
     * 前提：assert / input 节点带 alwaysRun=true。
     * 期望：error 含「不允许设置 alwaysRun」。
     */
    @Test
    @Order(20)
    @DisplayName("alwaysRun 挂在非法节点类型报错")
    void alwaysRun_disallowedNodeType_producesError() {
        String assertGraph = """
                {
                  "meta":{},
                  "nodes": [
                    {"id":"e1","type":"scenario-entry","name":"入口","position":{"x":0,"y":0},"data":{"scenarios":[{"id":"s1","name":"默认","envId":"","inSeed":{}}]}},
                    {"id":"1","type":"http","name":"h","position":{"x":0,"y":0},"data":{"callMode":"external","externalUrl":"https://e.example/a","httpMethod":"GET"}},
                    {"id":"2","type":"assert","name":"a","position":{"x":100,"y":0},"data":{"alwaysRun":true,"rules":[{"left":"http.body.code","operator":"eq","right":"0"}]}}
                  ],
                  "edges":[{"id":"e1","source":"1","target":"2"}]
                }
                """;
        GraphValidationResult assertResult = validator.validateJson(assertGraph);
        assertFalse(assertResult.isOk());
        assertTrue(assertResult.getErrors().stream()
                .anyMatch(e -> e.contains("alwaysRun") && e.contains("不允许设置")));

        String inputGraph = """
                {
                  "meta":{},
                  "nodes": [
                    {"id":"e1","type":"scenario-entry","name":"入口","position":{"x":0,"y":0},"data":{"scenarios":[{"id":"s1","name":"默认","envId":"","inSeed":{}}]}},
                    {"id":"1","type":"input","name":"i","position":{"x":0,"y":0},"data":{"alwaysRun":true,"prompt":"填","fields":[{"name":"x","label":"X","type":"text"}]}}
                  ],
                  "edges":[{"id":"e0","source":"e1","target":"1"}]
                }
                """;
        GraphValidationResult inputResult = validator.validateJson(inputGraph);
        assertFalse(inputResult.isOk());
        assertTrue(inputResult.getErrors().stream()
                .anyMatch(e -> e.contains("alwaysRun") && e.contains("不允许设置")));
    }

    /**
     * 前提：HTTP 节点 snapshotBefore 为字符串；delay 带 snapshotBefore=true。
     * 期望：分别报「须为 JSON 布尔」与「不允许设置」。
     */
    @Test
    @Order(21)
    @DisplayName("snapshotBefore 非法类型与非法节点报错")
    void snapshotBefore_typeAndNodeRules() {
        GraphValidationResult str = validator.validateJson(minimalHttpGraph("\"snapshotBefore\":\"true\""));
        assertFalse(str.isOk());
        assertTrue(str.getErrors().stream()
                .anyMatch(e -> e.contains("snapshotBefore") && e.contains("须为 JSON 布尔")));

        String delayGraph = """
                {
                  "meta":{},
                  "nodes": [
                    {"id":"e1","type":"scenario-entry","name":"入口","position":{"x":0,"y":0},"data":{"scenarios":[{"id":"s1","name":"默认","envId":"","inSeed":{}}]}},
                    {"id":"1","type":"delay","name":"d","position":{"x":0,"y":0},"data":{"ms":10,"snapshotBefore":true}}
                  ],
                  "edges":[{"id":"e0","source":"e1","target":"1"}]
                }
                """;
        GraphValidationResult delay = validator.validateJson(delayGraph);
        assertFalse(delay.isOk());
        assertTrue(delay.getErrors().stream()
                .anyMatch(e -> e.contains("snapshotBefore") && e.contains("不允许设置")));
    }

    /**
     * 前提：节点无顶层 name。
     * 期望：报错含「缺少 name」。
     */
    @Test
    @Order(22)
    @DisplayName("缺顶层 name 校验失败")
    void missingTopLevelName_fails() {
        String json = """
                {
                  "nodes": [
                    {"id":"1","type":"delay","position":{"x":0,"y":0},"data":{"ms":10}}
                  ],
                  "edges":[{"id":"e0","source":"e1","target":"1"}]
                }
                """;
        GraphValidationResult result = validator.validateJson(json);
        assertFalse(result.isOk());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("缺少 name")));
    }

    /**
     * 前提：http-return 缺 from。
     * 期望：报错缺少 from。
     */
    @Test
    @Order(24)
    @DisplayName("http-return 缺 from 失败")
    void httpReturnMissingFrom_fails() {
        String json = """
                {
                  "nodes": [
                    {"id":"1","type":"http-return","name":"写出","position":{"x":0,"y":0},"data":{}}
                  ],
                  "edges":[{"id":"e0","source":"e1","target":"1"}]
                }
                """;
        GraphValidationResult result = validator.validateJson(json);
        assertFalse(result.isOk());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("缺少 from")));
    }

    /**
     * 前提：http-entry 缺 method / path / envId。
     * 期望：全量校验分别报须配置。
     */
    @Test
    @Order(30)
    @DisplayName("http-entry 缺 method/path/envId 报错")
    void httpEntry_missingFields_producesErrors() {
        String json = """
                {
                  "nodes": [
                    {"id":"e1","type":"http-entry","name":"入站","position":{"x":0,"y":0},
                     "data":{"fallbackOut":{"status":500,"headers":{},"body":{}}}}
                  ],
                  "edges":[]
                }
                """;
        GraphValidationResult result = validator.validate(GraphJson.parse(json));
        assertFalse(result.isOk());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("须配置 method")));
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("须配置非空 path")));
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("须配置 envId")));
    }

    /**
     * 前提：http-entry 的 fallbackOut 缺 status。
     * 期望：报 fallbackOut.status 须为 100–599。
     */
    @Test
    @Order(31)
    @DisplayName("http-entry fallbackOut 缺 status 报错")
    void httpEntry_fallbackOutMissingStatus_producesError() {
        String json = """
                {
                  "nodes": [
                    {"id":"e1","type":"http-entry","name":"入站","position":{"x":0,"y":0},
                     "data":{"method":"POST","path":"/x","envId":"1",
                       "fallbackOut":{"headers":{},"body":{}}}}
                  ],
                  "edges":[]
                }
                """;
        GraphValidationResult result = validator.validate(GraphJson.parse(json));
        assertFalse(result.isOk());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("fallbackOut.status")));
    }

    /**
     * 前提：图含同序双 http-return；走 validateJson。
     * 期望：errors 含双返回拓扑文案（全量规则并入 validateJson）。
     */
    @Test
    @Order(32)
    @DisplayName("validateJson 跑全量双返回规则")
    void validateJson_includesSequentialDoubleReturn() {
        String json = """
                {
                  "nodes": [
                    {"id":"e1","type":"http-entry","name":"入站","position":{"x":0,"y":0},
                     "data":{"method":"POST","path":"/x","envId":"1",
                       "fallbackOut":{"status":500,"headers":{},"body":{}}}},
                    {"id":"r1","type":"http-return","name":"回1","position":{"x":100,"y":0},
                     "data":{"from":"flow.out","status":200}},
                    {"id":"r2","type":"http-return","name":"回2","position":{"x":200,"y":0},
                     "data":{"from":"flow.out","status":200}}
                  ],
                  "edges":[
                    {"id":"ed1","source":"e1","target":"r1"},
                    {"id":"ed2","source":"r1","target":"r2"}
                  ]
                }
                """;
        GraphValidationResult result = validator.validateJson(json);
        assertFalse(result.isOk());
        assertTrue(result.getErrors().stream().anyMatch(e -> e.contains("同一路径上存在多个返回节点")));
    }

    /** 单节点外联 HTTP 图；extraData 为 data 内额外 JSON 片段（可空）。 */
    private static String minimalHttpGraph(String extraData) {
        String extra = extraData == null || extraData.isBlank() ? "" : "," + extraData;
        return """
                {
                  "meta":{},
                  "nodes": [
                    {"id":"e1","type":"scenario-entry","name":"入口","position":{"x":0,"y":0},"data":{"scenarios":[{"id":"s1","name":"默认","envId":"","inSeed":{}}]}},
                    {"id":"1","type":"http","name":"h","position":{"x":0,"y":0},"data":{"callMode":"external","externalUrl":"https://e.example/a","httpMethod":"GET"%s}}
                  ],
                  "edges":[{"id":"e0","source":"e1","target":"1"}]
                }
                """.formatted(extra);
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
