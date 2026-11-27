package com.qualitest.flow.cache;

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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 测谁：ParsedGraphCache。
 * 边界：命中跳过 parse、Hub 失效后重载、删流返回 null。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=ParsedGraphCacheTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ParsedGraphCacheTest {

    private static final long FLOW_ID = 11L;
    private static final long PROJECT_ID = 1L;

    private TestFlowMapper testFlowMapper;
    private FlowExternalChangeHub hub;
    private FlowExternalChangePublisher publisher;
    private ParsedGraphCache cache;

    @BeforeEach
    void setUp() {
        testFlowMapper = mock(TestFlowMapper.class);
        hub = new FlowExternalChangeHub();
        publisher = new FlowExternalChangePublisher(hub, null);
        cache = new ParsedGraphCache(testFlowMapper, hub);
        cache.subscribe();
        cache.clear();
        cache.resetCounters();
    }

    /**
     * 前提：同一流连续 get 两次，revision 不变。
     * 期望：只 parse 一次；第二次返回同一 GraphJson 实例。
     */
    @Test
    @Order(1)
    @DisplayName("命中：第二次不 parse")
    void secondGet_skipsParse() {
        when(testFlowMapper.selectTestFlowById(FLOW_ID)).thenReturn(flow(1L));

        GraphJson first = cache.get(FLOW_ID);
        GraphJson second = cache.get(FLOW_ID);

        assertNotNull(first);
        assertSame(first, second);
        assertEquals(1, cache.getParseCount());
        assertEquals(2, cache.getLoadCount());
    }

    /**
     * 前提：已缓存后经 Hub 发 GRAPH_COMMITTED；库 revision 升为 2。
     * 期望：失效后再次 get 会重新 parse。
     */
    @Test
    @Order(2)
    @DisplayName("图提交后失效再加载")
    void graphCommitted_invalidates() {
        when(testFlowMapper.selectTestFlowById(FLOW_ID)).thenReturn(flow(1L));
        cache.get(FLOW_ID);
        assertEquals(1, cache.getParseCount());

        publisher.publishGraphCommitted(FLOW_ID, PROJECT_ID, FlowExternalChangeEvent.SOURCE_WEB_SAVE,
                null, null, 2L);

        when(testFlowMapper.selectTestFlowById(FLOW_ID)).thenReturn(flow(2L));
        GraphJson reloaded = cache.get(FLOW_ID);
        assertNotNull(reloaded);
        assertEquals(2, cache.getParseCount());
    }

    /**
     * 前提：流已逻辑删除。
     * 期望：get 返回 null 且缓存清空。
     */
    @Test
    @Order(3)
    @DisplayName("删流后 get 为 null")
    void deletedFlow_returnsNull() {
        when(testFlowMapper.selectTestFlowById(FLOW_ID)).thenReturn(flow(1L));
        assertNotNull(cache.get(FLOW_ID));

        publisher.publishFlowDeleted(FLOW_ID, PROJECT_ID, FlowExternalChangeEvent.SOURCE_WEB_UI);
        TestFlow deleted = flow(1L);
        deleted.setDelStatus(1);
        when(testFlowMapper.selectTestFlowById(FLOW_ID)).thenReturn(deleted);

        assertNull(cache.get(FLOW_ID));
        assertEquals(0, cache.size());
    }

    private static TestFlow flow(long revision) {
        GraphJson g = new GraphJson();
        GraphNode n = new GraphNode();
        n.setId("n1");
        n.setType(FlowNodeType.ASSIGN.getCode());
        n.setName("赋值");
        g.setNodes(List.of(n));
        return TestFlow.builder()
                .testFlowId(FLOW_ID)
                .testProjectId(PROJECT_ID)
                .flowName("流A")
                .graphJson(g.toJsonString())
                .graphRevision(revision)
                .delStatus(0)
                .build();
    }
}
