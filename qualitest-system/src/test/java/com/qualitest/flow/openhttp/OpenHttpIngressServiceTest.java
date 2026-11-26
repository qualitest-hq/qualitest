package com.qualitest.flow.openhttp;

import com.qualitest.flow.delivery.InboundSlotRegistry;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphMeta;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.model.GraphNodePosition;
import com.qualitest.flow.node.NodeHandlerRegistry;
import com.qualitest.flow.run.FlowGraphRunner;
import com.qualitest.flow.run.RunPersistenceService;
import com.qualitest.flow.run.RunStatusUpdater;
import com.qualitest.flow.run.StepResultWriter;
import com.qualitest.flow.validate.FlowRunReadinessGate;
import com.qualitest.project.mapper.TestProjectMapper;
import com.qualitest.project.service.ITestFlowRunService;
import com.qualitest.project.service.ITestProjectEnvService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 测 OpenHttpIngressService：占到入站名额后、「线程池接手前」的失败必须交回名额并回平台错包。
 * 边界：mock 路由目录 / 就绪门禁 / 落库与状态依赖；不连 DB，不跑真实图。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=OpenHttpIngressServiceTest
 */
@ExtendWith(MockitoExtension.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("开放入站名额与准备失败收口")
class OpenHttpIngressServiceTest {

    private static final Long PROJECT_ID = 1L;
    private static final Long FLOW_ID = 10L;
    private static final String ENTRY_NODE_ID = "e-http";
    private static final String METHOD = "POST";
    private static final String PATH = "/v3/refund";

    @Mock
    private OpenHttpRouteRegistry routeRegistry;
    @Mock
    private FlowGraphRunner flowGraphRunner;
    @Mock
    private NodeHandlerRegistry nodeHandlerRegistry;
    @Mock
    private FlowRunReadinessGate flowRunReadinessGate;
    @Mock
    private ITestProjectEnvService testProjectEnvService;
    @Mock
    private TestProjectMapper testProjectMapper;
    @Mock
    private ITestFlowRunService testFlowRunService;
    @Mock
    private RunPersistenceService runPersistenceService;
    @Mock
    private StepResultWriter stepResultWriter;
    @Mock
    private RunStatusUpdater runStatusUpdater;

    /** 名额上限故意设小：泄漏会立刻表现为 heldCount 不为 0 */
    private final InboundSlotRegistry slots = new InboundSlotRegistry(4);

    private OpenHttpIngressService service;

    @BeforeEach
    void setUp() {
        service = new OpenHttpIngressService(routeRegistry, slots, flowGraphRunner, nodeHandlerRegistry,
                flowRunReadinessGate, testProjectEnvService, testProjectMapper, testFlowRunService,
                runPersistenceService, stepResultWriter, runStatusUpdater);
    }

    /**
     * 前提：路由与就绪检查通过，写 Run 行时抛异常。
     * 期望：名额归零，调用方收到 502 平台错包。
     */
    @Test
    @Order(1)
    @DisplayName("Run 落库抛异常时释放名额并回 502")
    void insertRunFails_releasesSlotAndReturnsBadGateway() {
        when(routeRegistry.resolve(PROJECT_ID, METHOD, PATH)).thenReturn(route());
        when(flowRunReadinessGate.collectBlockingErrors(any(), eq(PROJECT_ID), any())).thenReturn(List.of());
        doThrow(new RuntimeException("落库失败")).when(testFlowRunService).insertTestFlowRun(any());

        DeferredResult<ResponseEntity<Object>> result = service.handle(
                PROJECT_ID, METHOD, PATH, Map.of(), Map.of(), Map.of("orderId", "o-1"));

        assertTrue(result.isSetOrExpired(), "准备失败须立刻给调用方响应");
        assertEquals(502, statusOf(result));
        assertEquals(0, slots.heldCount(), "落库失败不得占死入站名额");
    }

    /**
     * 前提：素材查询抛异常，尚未走到 Run 落库。
     * 期望：不写 Run 行（无 RUNNING 幽灵行），名额归零并回 502。
     */
    @Test
    @Order(2)
    @DisplayName("落库前准备失败不留 Run 行且释放名额")
    void prepareFailsBeforeInsert_noGhostRunAndSlotReleased() {
        when(routeRegistry.resolve(PROJECT_ID, METHOD, PATH)).thenReturn(route());
        when(flowRunReadinessGate.collectBlockingErrors(any(), eq(PROJECT_ID), any())).thenReturn(List.of());
        when(testProjectMapper.selectAssetVariablesByTestProjectId(PROJECT_ID))
                .thenThrow(new RuntimeException("素材查询失败"));

        DeferredResult<ResponseEntity<Object>> result = service.handle(
                PROJECT_ID, METHOD, PATH, Map.of(), Map.of(), Map.of());

        assertTrue(result.isSetOrExpired());
        assertEquals(502, statusOf(result));
        assertEquals(0, slots.heldCount());
        verify(testFlowRunService, never()).insertTestFlowRun(any());
    }

    /**
     * 前提：路由目录查不到该 method+path。
     * 期望：直接 404，且不占用任何入站名额。
     */
    @Test
    @Order(3)
    @DisplayName("未登记路由时不占名额直接 404")
    void routeMissing_noSlotAcquired() {
        when(routeRegistry.resolve(PROJECT_ID, METHOD, PATH)).thenReturn(null);

        DeferredResult<ResponseEntity<Object>> result = service.handle(
                PROJECT_ID, METHOD, PATH, Map.of(), Map.of(), Map.of());

        assertTrue(result.isSetOrExpired());
        assertEquals(404, statusOf(result));
        assertEquals(0, slots.heldCount());
    }

    /** 取 DeferredResult 中平台错包的状态码 */
    private static int statusOf(DeferredResult<ResponseEntity<Object>> result) {
        ResponseEntity<?> response = (ResponseEntity<?>) result.getResult();
        return response != null && response.getStatusCode() != null
                ? response.getStatusCode().value()
                : -1;
    }

    /** 最小开放路由：入口节点 id 为 e-http，无环境、无 inputShape */
    private static OpenHttpRoute route() {
        GraphNode entry = new GraphNode();
        entry.setId(ENTRY_NODE_ID);
        entry.setType("http-entry");
        entry.setName("退款入站");
        entry.setPosition(new GraphNodePosition(0, 0));
        entry.setData(new HashMap<>(Map.of("method", METHOD, "path", PATH)));

        GraphJson graph = new GraphJson();
        graph.setNodes(List.of(entry));
        graph.setEdges(List.of());
        graph.setMeta(new GraphMeta());

        return new OpenHttpRoute(PROJECT_ID, FLOW_ID, ENTRY_NODE_ID, METHOD, PATH, null, graph);
    }
}
