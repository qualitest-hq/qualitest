package com.qualitest.flow.openhttp;

import com.qualitest.flow.cache.ParsedGraphCache;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.sync.FlowExternalChangeEvent;
import com.qualitest.flow.sync.FlowExternalChangeHub;
import com.qualitest.flow.sync.FlowExternalChangePublisher;
import com.qualitest.flow.model.FlowNodeType;
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

    /**
     * 前提：两流同占 POST /dup（脏数据）。
     * 期望：find 不命中；listPeerRoutes 仍暴露两条占用。
     */
    @Test
    @Order(4)
    @DisplayName("双活路由入站不命中且 peers 全量暴露")
    void ambiguousRoute_findNull_peersKeepBoth() {
        TestFlow a = httpFlow(FLOW_ID, "A流", "POST", "/dup", 1L);
        TestFlow b = httpFlow(71L, "B流", "POST", "/dup", 1L);
        when(testFlowMapper.selectTestFlowList(any())).thenReturn(List.of(a, b));

        assertNull(catalog.find(PROJECT_ID, "POST", "/dup"));
        assertEquals(2, index.listPeerRoutes(PROJECT_ID).size());
    }

    /**
     * 前提：同一流内两个 http-entry 写同一 method+path（脏数据）。
     * 期望：入站不命中（404）；预检能列出冲突键。
     */
    @Test
    @Order(5)
    @DisplayName("同流内同键也不命中且预检可见")
    void sameFlowDuplicate_findNull_preflightVisible() {
        when(testFlowMapper.selectTestFlowList(any())).thenReturn(List.of(sameFlowDuplicate()));
        when(testFlowMapper.selectTestFlowById(FLOW_ID)).thenReturn(sameFlowDuplicate());

        assertNull(catalog.find(PROJECT_ID, "POST", "/dup"));
        assertEquals(List.of("POST /dup"), index.findAmbiguousRouteKeys(PROJECT_ID));
    }

    /**
     * 前提：入口 path 写成 /a//b/；请求按 /a/b 访问。
     * 期望：规范化后命中同一路由（内部斜杠折叠、尾斜杠剥除）。
     */
    @Test
    @Order(6)
    @DisplayName("路径规范化：折叠连续斜杠并可查")
    void normalizePath_collapsesSlashes() {
        when(testFlowMapper.selectTestFlowList(any()))
                .thenReturn(List.of(httpFlow("POST", "/a//b/", 1L)));
        when(testFlowMapper.selectTestFlowById(FLOW_ID)).thenReturn(httpFlow("POST", "/a//b/", 1L));

        OpenHttpRoute route = catalog.find(PROJECT_ID, "POST", "/a/b");

        assertNotNull(route);
        assertEquals("/a/b", route.path());
    }

    /**
     * 前提：入口 path 误带 query。
     * 期望：规范化剥掉 query，按纯 path 命中。
     */
    @Test
    @Order(7)
    @DisplayName("路径规范化：剥掉 query 后命中")
    void normalizePath_stripsQuery() {
        when(testFlowMapper.selectTestFlowList(any()))
                .thenReturn(List.of(httpFlow("GET", "/x?a=1", 1L)));
        when(testFlowMapper.selectTestFlowById(FLOW_ID)).thenReturn(httpFlow("GET", "/x?a=1", 1L));

        assertNotNull(catalog.find(PROJECT_ID, "GET", "/x"));
    }

    /** 同一流两个入口写同一 method+path */
    private static TestFlow sameFlowDuplicate() {
        GraphJson g = new GraphJson();
        GraphNode first = new GraphNode();
        first.setId("entry-1");
        first.setType(FlowNodeType.HTTP_ENTRY.getCode());
        first.setName("入口一");
        first.setData(Map.of("method", "POST", "path", "/dup",
                "fallbackOut", Map.of("status", 500, "headers", Map.of(), "body", Map.of())));
        GraphNode second = new GraphNode();
        second.setId("entry-2");
        second.setType(FlowNodeType.HTTP_ENTRY.getCode());
        second.setName("入口二");
        second.setData(Map.of("method", "POST", "path", "/dup",
                "fallbackOut", Map.of("status", 500, "headers", Map.of(), "body", Map.of())));
        g.setNodes(List.of(first, second));
        return TestFlow.builder()
                .testFlowId(FLOW_ID)
                .testProjectId(PROJECT_ID)
                .flowName("同流重复")
                .graphJson(g.toJsonString())
                .graphRevision(1L)
                .delStatus(0)
                .build();
    }

    private static TestFlow httpFlow(String method, String path, long revision) {
        return httpFlow(FLOW_ID, "HTTP流", method, path, revision);
    }

    private static TestFlow httpFlow(long flowId, String flowName, String method, String path, long revision) {
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
                .testFlowId(flowId)
                .testProjectId(PROJECT_ID)
                .flowName(flowName)
                .graphJson(g.toJsonString())
                .graphRevision(revision)
                .delStatus(0)
                .build();
    }
}
