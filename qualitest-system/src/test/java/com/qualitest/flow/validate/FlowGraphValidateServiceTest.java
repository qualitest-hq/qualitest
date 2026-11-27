package com.qualitest.flow.validate;

import com.qualitest.flow.openhttp.OpenHttpRouteRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 测谁：FlowGraphValidateService 按 stage 产出 issues。
 * 边界：SAVE 永不因图 error 出 issue；CANVAS 走 raw + 鉴权；RUN 含路由冲突；deferTopology 延后开始节点。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=FlowGraphValidateServiceTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlowGraphValidateServiceTest {

    private FlowGraphValidateService service;
    private OpenHttpRouteRegistry routeRegistry;
    private FlowRunReadinessGate readiness;

    @BeforeEach
    void setUp() {
        routeRegistry = mock(OpenHttpRouteRegistry.class);
        readiness = mock(FlowRunReadinessGate.class);
        when(readiness.collectRawBlockingIssues(any(), any(), any(), eq(FlowIssue.Stage.RUN)))
                .thenReturn(List.of(FlowIssue.error(
                        FlowIssueCodes.ENTRY_ROUTE_CONFLICT,
                        FlowIssue.Stage.RUN,
                        null,
                        "开放 HTTP 入口冲突：POST /dup")));
        when(readiness.collectRawBlockingIssues(any(), any(), any(), eq(FlowIssue.Stage.INGRESS)))
                .thenReturn(List.of(FlowIssue.error(
                        FlowIssueCodes.ENTRY_ROUTE_CONFLICT,
                        FlowIssue.Stage.INGRESS,
                        null,
                        "开放 HTTP 入口冲突：POST /dup")));
        when(readiness.collectRawBlockingIssues(any(), any(), any(), eq(FlowIssue.Stage.SUBFLOW_CALL)))
                .thenReturn(List.of(FlowIssue.error(
                        FlowIssueCodes.GRAPH_STRUCTURE,
                        FlowIssue.Stage.SUBFLOW_CALL,
                        null,
                        "未找到开始节点（入口须无入边）")));
        when(readiness.collectAuthIssues(any(), any(), eq(FlowIssue.Stage.CANVAS)))
                .thenReturn(List.of());
        when(readiness.collectAssertPathIssues(any(), eq(FlowIssue.Stage.CANVAS)))
                .thenReturn(List.of());
        service = FlowGraphValidateServiceTestSupport.create(
                new GraphJsonValidator(), readiness, routeRegistry);
    }

    /**
     * 前提：含本流路由自冲突的残缺图。
     * 期望：stage=save 返回空 issues。
     */
    @Test
    @Order(1)
    @DisplayName("SAVE 阶段不因图 error 出 issue")
    void saveStageAlwaysEmptyIssues() {
        String json = """
                {"nodes":[
                   {"id":"e1","type":"http-entry","name":"A","position":{"x":0,"y":0},
                    "data":{"method":"POST","path":"/dup","envId":"1","fallbackOut":{"status":500,"headers":{},"body":{}}}},
                   {"id":"e2","type":"http-entry","name":"B","position":{"x":1,"y":0},
                    "data":{"method":"POST","path":"/dup","envId":"1","fallbackOut":{"status":500,"headers":{},"body":{}}}}
                 ],"edges":[]}
                """;
        FlowGraphValidateResult result =
                service.validate(json, FlowIssue.Stage.SAVE, 1L, 10L);
        assertFalse(result.hasErrors());
        assertTrue(result.getIssues().isEmpty());
    }

    /**
     * 前提：mock 开跑门禁返回路由冲突。
     * 期望：stage=run 带 ENTRY_ROUTE_CONFLICT。
     */
    @Test
    @Order(2)
    @DisplayName("RUN 阶段返回结构化路由冲突")
    void runStageReturnsRouteConflictCode() {
        FlowGraphValidateResult result = service.validate(
                "{\"nodes\":[],\"edges\":[]}", FlowIssue.Stage.RUN, 1L, 10L);
        assertTrue(result.hasErrors());
        assertEquals(FlowIssueCodes.ENTRY_ROUTE_CONFLICT, result.getIssues().get(0).getCode());
        assertEquals(FlowIssue.Stage.RUN, result.getIssues().get(0).getStage());
    }

    /**
     * 前提：边带未知字段（typed 会丢掉）。
     * 期望：canvas 走 validateJson(raw) 仍报 GRAPH_STRUCTURE。
     */
    @Test
    @Order(3)
    @DisplayName("CANVAS raw 校验保留边多余字段错误")
    void canvasStageReportsEdgeExtraFieldViaRaw() {
        when(routeRegistry.conflicts(any(), any(), any(), eq(FlowIssue.Stage.CANVAS)))
                .thenReturn(List.of());
        String json = """
                {"nodes":[
                   {"id":"se","type":"scenario-entry","name":"入口","position":{"x":0,"y":0},
                    "data":{"scenarios":[{"id":"s1","name":"默认","envId":"","inSeed":{}}]}},
                   {"id":"h1","type":"http","name":"HTTP","position":{"x":100,"y":0},
                    "data":{"callMode":"external","externalUrl":"https://e.example","httpMethod":"GET"}}
                 ],
                 "edges":[{"id":"e1","source":"se","target":"h1","animated":true}]}
                """;
        FlowGraphValidateResult result =
                service.validate(json, FlowIssue.Stage.CANVAS, 1L, 10L, false);
        assertTrue(result.hasErrors());
        assertTrue(result.getIssues().stream()
                .anyMatch(i -> FlowIssueCodes.GRAPH_STRUCTURE.equals(i.getCode())
                        && i.getMessage() != null
                        && i.getMessage().contains("不允许的字段")
                        && i.getMessage().contains("animated")));
    }

    /**
     * 前提：有节点无入口；deferTopology=true。
     * 期望：不报开始节点类拓扑硬拦。
     */
    @Test
    @Order(4)
    @DisplayName("CANVAS deferTopology 延后开始节点硬拦")
    void canvasDeferTopologySkipsStartNodeErrors() {
        when(routeRegistry.conflicts(any(), any(), any(), eq(FlowIssue.Stage.CANVAS)))
                .thenReturn(List.of());
        String json = """
                {"nodes":[
                   {"id":"h1","type":"http","name":"孤立","position":{"x":0,"y":0},
                    "data":{"callMode":"external","externalUrl":"https://e.example","httpMethod":"GET"}}
                 ],"edges":[]}
                """;
        FlowGraphValidateResult deferred =
                service.validate(json, FlowIssue.Stage.CANVAS, 1L, 10L, true);
        assertFalse(deferred.hasErrors(), () -> "deferred issues=" + deferred.getIssues());

        FlowGraphValidateResult full =
                service.validate(json, FlowIssue.Stage.CANVAS, 1L, 10L, false);
        assertTrue(full.hasErrors());
        assertTrue(full.getIssues().stream()
                .anyMatch(i -> i.getMessage() != null && i.getMessage().contains("入口")));
    }

    /**
     * 前提：registry 返回路由冲突。
     * 期望：canvas issues 含 ENTRY_ROUTE_CONFLICT。
     */
    @Test
    @Order(5)
    @DisplayName("CANVAS 合并路由冲突 code")
    void canvasStageMergesRouteConflict() {
        when(routeRegistry.conflicts(any(), any(), any(), eq(FlowIssue.Stage.CANVAS)))
                .thenReturn(List.of(FlowIssue.error(
                        FlowIssueCodes.ENTRY_ROUTE_CONFLICT,
                        FlowIssue.Stage.CANVAS,
                        "e1",
                        "开放 HTTP 入口冲突：POST /dup")));
        String json = """
                {"nodes":[
                   {"id":"e1","type":"http-entry","name":"A","position":{"x":0,"y":0},
                    "data":{"method":"POST","path":"/dup","envId":"1",
                            "fallbackOut":{"status":500,"headers":{},"body":{}}}}
                 ],"edges":[]}
                """;
        FlowGraphValidateResult result =
                service.validate(json, FlowIssue.Stage.CANVAS, 1L, 10L, true);
        assertTrue(result.getIssues().stream()
                .anyMatch(i -> FlowIssueCodes.ENTRY_ROUTE_CONFLICT.equals(i.getCode())
                        && "e1".equals(i.getNodeId())));
    }

    /**
     * 前提：空 graphJson。
     * 期望：GRAPH_JSON_INVALID（解析地板短路，不经流检查定级）。
     */
    @Test
    @Order(6)
    @DisplayName("空 graphJson 返回 GRAPH_JSON_INVALID")
    void blankGraphJsonInvalid() {
        FlowGraphValidateResult result =
                service.validate("  ", FlowIssue.Stage.CANVAS, null, null);
        assertTrue(result.hasErrors());
        assertEquals(FlowIssueCodes.GRAPH_JSON_INVALID, result.getIssues().get(0).getCode());
        assertTrue(result.getIssues().get(0).isError());
    }

    /**
     * 前提：非法 JSON 文本；SAVE / CANVAS / RUN。
     * 期望：均为 GRAPH_JSON_INVALID ERROR（短路，不被 profile 影响）。
     */
    @Test
    @Order(9)
    @DisplayName("坏 JSON 各 stage 短路为 GRAPH_JSON_INVALID")
    void malformedJsonShortCircuitsAllStages() {
        String bad = "{not-json";
        FlowGraphValidateResult save = service.validate(bad, FlowIssue.Stage.SAVE, 1L, 10L);
        assertEquals(1, save.getIssues().size());
        assertEquals(FlowIssueCodes.GRAPH_JSON_INVALID, save.getIssues().get(0).getCode());
        assertTrue(save.getIssues().get(0).isError());

        FlowGraphValidateResult canvas = service.validate(bad, FlowIssue.Stage.CANVAS, 1L, 10L);
        assertEquals(1, canvas.getIssues().size());
        assertEquals(FlowIssueCodes.GRAPH_JSON_INVALID, canvas.getIssues().get(0).getCode());
        assertTrue(canvas.getIssues().get(0).isError());

        FlowGraphValidateResult run = service.validate(bad, FlowIssue.Stage.RUN, 1L, 10L);
        assertEquals(1, run.getIssues().size());
        assertEquals(FlowIssueCodes.GRAPH_JSON_INVALID, run.getIssues().get(0).getCode());
        assertTrue(run.getIssues().get(0).isError());
    }

    /**
     * 前提：mock 断言路径门禁返回错误。
     * 期望：canvas issues 含 ASSERT_PATH。
     */
    @Test
    @Order(10)
    @DisplayName("CANVAS 合并 ASSERT_PATH")
    void canvasStageMergesAssertPath() {
        when(routeRegistry.conflicts(any(), any(), any(), eq(FlowIssue.Stage.CANVAS)))
                .thenReturn(List.of());
        when(readiness.collectAssertPathIssues(any(), eq(FlowIssue.Stage.CANVAS)))
                .thenReturn(List.of(FlowIssue.error(
                        FlowIssueCodes.ASSERT_PATH,
                        FlowIssue.Stage.CANVAS,
                        null,
                        "ASSERT_PATH: 断言路径非法")));
        String json = """
                {"nodes":[
                   {"id":"se","type":"scenario-entry","name":"入口","position":{"x":0,"y":0},
                    "data":{"scenarios":[{"id":"s1","name":"默认","envId":"","inSeed":{}}]}},
                   {"id":"a1","type":"assert","name":"断言","position":{"x":100,"y":0},
                    "data":{"rules":[]}}
                 ],
                 "edges":[{"id":"e1","source":"se","target":"a1"}]}
                """;
        FlowGraphValidateResult result =
                service.validate(json, FlowIssue.Stage.CANVAS, 1L, 10L, false);
        assertTrue(result.getIssues().stream()
                .anyMatch(i -> FlowIssueCodes.ASSERT_PATH.equals(i.getCode())));
    }

    /**
     * 前提：ingress / subflow stage。
     * 期望：走 run-like 门禁并保留 stage。
     */
    @Test
    @Order(7)
    @DisplayName("INGRESS / SUBFLOW 走 run-like 并保留 stage")
    void ingressAndSubflowStages() {
        FlowGraphValidateResult ingress = service.validate(
                "{\"nodes\":[],\"edges\":[]}", FlowIssue.Stage.INGRESS, 1L, 10L);
        assertEquals(FlowIssue.Stage.INGRESS, ingress.getIssues().get(0).getStage());
        assertEquals(FlowIssueCodes.ENTRY_ROUTE_CONFLICT, ingress.getIssues().get(0).getCode());

        FlowGraphValidateResult subflow = service.validate(
                "{\"nodes\":[],\"edges\":[]}", FlowIssue.Stage.SUBFLOW_CALL, 1L, 10L);
        assertEquals(FlowIssue.Stage.SUBFLOW_CALL, subflow.getIssues().get(0).getStage());
        assertEquals(FlowIssueCodes.GRAPH_STRUCTURE, subflow.getIssues().get(0).getCode());
    }

    /**
     * 前提：canvas 校验；readiness 返回 AUTH 文案。
     * 期望：issues 含 RUN_READINESS_AUTH（并进图 error）。
     */
    @Test
    @Order(8)
    @DisplayName("CANVAS 合并鉴权图 error")
    void canvasStageMergesAuthIssues() {
        when(routeRegistry.conflicts(any(), any(), any(), eq(FlowIssue.Stage.CANVAS)))
                .thenReturn(List.of());
        when(readiness.collectAuthIssues(any(), any(), eq(FlowIssue.Stage.CANVAS)))
                .thenReturn(List.of(FlowIssue.error(
                        FlowIssueCodes.RUN_READINESS_AUTH,
                        FlowIssue.Stage.CANVAS,
                        null,
                        "AUTH_TOKEN_MISSING: 缺 token")));
        String json = """
                {"nodes":[
                   {"id":"se","type":"scenario-entry","name":"入口","position":{"x":0,"y":0},
                    "data":{"scenarios":[{"id":"s1","name":"默认","envId":"","inSeed":{}}]}},
                   {"id":"h1","type":"http","name":"HTTP","position":{"x":100,"y":0},
                    "data":{"callMode":"external","externalUrl":"https://e.example","httpMethod":"GET"}}
                 ],
                 "edges":[{"id":"e1","source":"se","target":"h1"}]}
                """;
        FlowGraphValidateResult result =
                service.validate(json, FlowIssue.Stage.CANVAS, 1L, 10L, false);
        assertTrue(result.getIssues().stream()
                .anyMatch(i -> FlowIssueCodes.RUN_READINESS_AUTH.equals(i.getCode())
                        && i.getMessage() != null
                        && i.getMessage().startsWith("AUTH_TOKEN_MISSING:")));
    }
}
