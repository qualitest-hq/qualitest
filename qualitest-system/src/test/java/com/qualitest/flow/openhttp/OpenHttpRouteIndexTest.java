package com.qualitest.flow.openhttp;

import com.qualitest.flow.cache.ParsedGraphCache;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.sync.FlowExternalChangeEvent;
import com.qualitest.flow.sync.FlowExternalChangeHub;
import com.qualitest.flow.sync.FlowExternalChangePublisher;
import com.qualitest.flow.validate.FlowNodeType;
import com.qualitest.project.domain.TestFlow;
import com.qualitest.project.mapper.TestFlowMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 测谁：OpenHttpRouteIndex + Catalog 门面。
 * 边界：二次查找不重建、图变更失效、删流后 404。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=OpenHttpRouteIndexTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OpenHttpRouteIndexTest {

    private static final long PROJECT_ID = 7L;
    private static final long FLOW_ID = 70L;

    private TestFlowMapper testFlowMapper;
    private FlowExternalChangeHub hub;
    private FlowExternalChangePublisher publisher;
    private ParsedGraphCache graphCache;
    private OpenHttpRouteIndex index;
    private OpenHttpRouteCatalog catalog;

    @BeforeEach
    void setUp() {
        testFlowMapper = mock(TestFlowMapper.class);
        hub = new FlowExternalChangeHub();
        publisher = new FlowExternalChangePublisher(hub, null);
        graphCache = new ParsedGraphCache(testFlowMapper, hub);
        graphCache.subscribe();
        index = new OpenHttpRouteIndex(testFlowMapper, graphCache, hub);
        index.subscribe();
        catalog = new OpenHttpRouteCatalog(index);
        index.clear();
        index.resetCounters();
        graphCache.clear();
        graphCache.resetCounters();
    }

    /**
     * 前提：项目下一流带 POST /order。
     * 期望：连续 find 两次只重建一次索引。
     */
    @Test
    @Order(1)
    @DisplayName("二次查找不扫项目流列表")
    void secondFind_noRebuild() {
        when(testFlowMapper.selectTestFlowList(any())).thenReturn(List.of(httpFlow("POST", "/order", 1L)));
        when(testFlowMapper.selectTestFlowById(FLOW_ID)).thenReturn(httpFlow("POST", "/order", 1L));

        OpenHttpRoute first = catalog.find(PROJECT_ID, "POST", "/order");
        OpenHttpRoute second = catalog.find(PROJECT_ID, "POST", "/order");

        assertNotNull(first);
        assertNotNull(second);
        assertEquals(FLOW_ID, second.testFlowId());
        assertEquals(1, index.getRebuildCount());
    }

    /**
     * 前提：已索引 /order；提交后 path 改为 /order/v2。
     * 期望：旧 path 404，新 path 可达。
     */
    @Test
    @Order(2)
    @DisplayName("改 path 后旧路由失效")
    void pathChange_invalidatesOldRoute() {
        when(testFlowMapper.selectTestFlowList(any())).thenReturn(List.of(httpFlow("POST", "/order", 1L)));
        when(testFlowMapper.selectTestFlowById(FLOW_ID)).thenReturn(httpFlow("POST", "/order", 1L));
        assertNotNull(catalog.find(PROJECT_ID, "POST", "/order"));

        publisher.publishGraphCommitted(FLOW_ID, PROJECT_ID, FlowExternalChangeEvent.SOURCE_WEB_SAVE,
                null, null, 2L);

        TestFlow updated = httpFlow("POST", "/order/v2", 2L);
        when(testFlowMapper.selectTestFlowList(any())).thenReturn(List.of(updated));
        when(testFlowMapper.selectTestFlowById(FLOW_ID)).thenReturn(updated);

        assertNull(catalog.find(PROJECT_ID, "POST", "/order"));
        OpenHttpRoute neu = catalog.find(PROJECT_ID, "POST", "/order/v2");
        assertNotNull(neu);
        assertEquals("/order/v2", neu.path());
        assertEquals(2, index.getRebuildCount());
    }

    /**
     * 前提：已索引；发 FLOW_DELETED 且列表为空。
     * 期望：find 返回 null（404）。
     */
    @Test
    @Order(3)
    @DisplayName("删流后路由 404")
    void flowDeleted_returnsNull() {
        when(testFlowMapper.selectTestFlowList(any())).thenReturn(List.of(httpFlow("GET", "/ping", 1L)));
        when(testFlowMapper.selectTestFlowById(FLOW_ID)).thenReturn(httpFlow("GET", "/ping", 1L));
        assertNotNull(catalog.find(PROJECT_ID, "GET", "/ping"));

        publisher.publishFlowDeleted(FLOW_ID, PROJECT_ID, FlowExternalChangeEvent.SOURCE_WEB_UI);
        when(testFlowMapper.selectTestFlowList(any())).thenReturn(List.of());
        when(testFlowMapper.selectTestFlowById(FLOW_ID)).thenReturn(null);

        assertNull(catalog.find(PROJECT_ID, "GET", "/ping"));
    }

    private static TestFlow httpFlow(String method, String path, long revision) {
        GraphJson g = new GraphJson();
        GraphNode n = new GraphNode();
        n.setId("entry-1");
        n.setType(FlowNodeType.HTTP_ENTRY.getCode());
        n.setName("开放入口");
        n.setData(Map.of(
                "method", method,
                "path", path,
                "fallbackOut", Map.of("status", 500, "headers", Map.of(), "body", Map.of())));
        g.setNodes(List.of(n));
        return TestFlow.builder()
                .testFlowId(FLOW_ID)
                .testProjectId(PROJECT_ID)
                .flowName("HTTP流")
                .graphJson(g.toJsonString())
                .graphRevision(revision)
                .delStatus(0)
                .build();
    }
}
