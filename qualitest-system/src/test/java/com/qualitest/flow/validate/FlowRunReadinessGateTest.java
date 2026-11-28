package com.qualitest.flow.validate;

import com.qualitest.flow.cache.ParsedGraphCache;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.openhttp.OpenHttpRouteRegistry;
import com.qualitest.project.mapper.TestProjectApiMapper;
import com.qualitest.project.mapper.TestProjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 测谁：FlowRunReadinessGate 的路由冲突原料收集（不定级）。
 * 边界：只产 raw issues；定级与硬拦文案由 FlowGraphValidateService 负责。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=FlowRunReadinessGateTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlowRunReadinessGateTest {

    private static final long PROJECT_ID = 7L;
    private static final long FLOW_ID = 70L;

    private OpenHttpRouteRegistry routeRegistry;
    private FlowRunReadinessGate gate;

    @BeforeEach
    void setUp() {
        routeRegistry = mock(OpenHttpRouteRegistry.class);
        when(routeRegistry.conflicts(any(), eq(PROJECT_ID), eq(FLOW_ID), any())).thenAnswer(inv -> {
            GraphJson graph = inv.getArgument(0);
            FlowIssue.Stage stage = inv.getArgument(3);
            List<FlowIssue> issues = new ArrayList<>();
            for (String message : OpenHttpRouteCollisionGate.validate(graph, FLOW_ID, List.of())) {
                issues.add(FlowIssue.error(FlowIssueCodes.ENTRY_ROUTE_CONFLICT, stage, null, message));
            }
            return issues;
        });
        gate = new FlowRunReadinessGate(
                new GraphJsonValidator(null),
                mock(TestProjectApiMapper.class),
                mock(TestProjectMapper.class),
                mock(ParsedGraphCache.class),
                routeRegistry);
    }

    /**
     * 前提：本流 HTTP 入口 POST /dup，项目内另一流已占同键。
     * 期望：raw 问题含「开放 HTTP 入口冲突」。
     */
    @Test
    @Order(1)
    @DisplayName("跨流路由冲突写入 raw issues")
    void collectRaw_crossFlowRouteConflict() {
        when(routeRegistry.conflicts(any(), eq(PROJECT_ID), eq(FLOW_ID), any())).thenReturn(List.of(
                FlowIssue.error(
                        FlowIssueCodes.ENTRY_ROUTE_CONFLICT,
                        FlowIssue.Stage.RUN,
                        null,
                        "开放 HTTP 入口冲突：POST /dup 已被流「旧流」的入口「旧入口」占用（本流节点「入站」）")));

        List<FlowIssue> issues = gate.collectRawBlockingIssues(
                httpGraph("/dup"), PROJECT_ID, FLOW_ID, FlowIssue.Stage.RUN);

        assertTrue(issues.stream().anyMatch(i ->
                i.getMessage() != null
                        && i.getMessage().contains("开放 HTTP 入口冲突")
                        && i.getMessage().contains("POST /dup")));
    }

    /**
     * 前提：本流两个 HTTP 入口写同一 method+path（同流重复）。
     * 期望：raw 问题提示本流内占用。
     */
    @Test
    @Order(2)
    @DisplayName("同流路由冲突写入 raw issues")
    void collectRaw_sameFlowRouteConflict() {
        List<FlowIssue> issues = gate.collectRawBlockingIssues(
                sameFlowDuplicateGraph(), PROJECT_ID, FLOW_ID, FlowIssue.Stage.RUN);

        assertTrue(issues.stream().anyMatch(i ->
                i.getMessage() != null
                        && i.getMessage().contains("开放 HTTP 入口冲突")
                        && i.getMessage().contains("在本流")
                        && i.getMessage().contains("POST /dup")));
    }

    /** 单 http-entry 图，路径可指定 */
    private static GraphJson httpGraph(String path) {
        return parse("""
                {"nodes":[{"id":"e1","type":"http-entry","name":"入站","position":{"x":0,"y":0},
                 "data":{"method":"POST","path":"%s","envId":"1",
                         "fallbackOut":{"status":500,"headers":{},"body":{}}}}],
                 "edges":[]}
                """.formatted(path));
    }

    /** 同流两个 http-entry 都写 POST /dup */
    private static GraphJson sameFlowDuplicateGraph() {
        return parse("""
                {"nodes":[
                   {"id":"e1","type":"http-entry","name":"入口一","position":{"x":0,"y":0},
                    "data":{"method":"POST","path":"/dup","envId":"1",
                            "fallbackOut":{"status":500,"headers":{},"body":{}}}},
                   {"id":"e2","type":"http-entry","name":"入口二","position":{"x":100,"y":0},
                    "data":{"method":"POST","path":"/dup","envId":"1",
                            "fallbackOut":{"status":500,"headers":{},"body":{}}}}
                 ],
                 "edges":[]}
                """);
    }

    private static GraphJson parse(String json) {
        return GraphJson.parse(json);
    }
}
