package com.qualitest.flow.cache;

import com.qualitest.flow.model.GraphJson;
import com.qualitest.flow.model.GraphNode;
import com.qualitest.flow.sync.FlowExternalChangeHub;
import com.qualitest.flow.model.FlowNodeType;
import com.qualitest.project.domain.TestFlow;
import com.qualitest.project.mapper.TestFlowMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 测谁：子流重复取图走 ParsedGraphCache.getFrom。
 * 边界：同 subflowId 连续 getFrom 只 parse 一次，且不二次查库。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=ParsedGraphCacheSubflowReuseTest
 */
class ParsedGraphCacheSubflowReuseTest {

    private static final long SUBFLOW_ID = 10086L;

    private TestFlowMapper mapper;
    private ParsedGraphCache cache;
    private TestFlow subflow;

    @BeforeEach
    void setUp() {
        mapper = mock(TestFlowMapper.class);
        GraphJson g = new GraphJson();
        GraphNode n = new GraphNode();
        n.setId("a1");
        n.setType(FlowNodeType.ASSIGN.getCode());
        g.setNodes(List.of(n));
        subflow = TestFlow.builder()
                .testFlowId(SUBFLOW_ID)
                .testProjectId(1L)
                .flowName("子流")
                .graphJson(g.toJsonString())
                .graphRevision(3L)
                .delStatus(0)
                .build();
        when(mapper.selectTestFlowById(SUBFLOW_ID)).thenReturn(subflow);

        FlowExternalChangeHub hub = new FlowExternalChangeHub();
        cache = new ParsedGraphCache(mapper, hub);
        cache.subscribe();
        cache.resetCounters();
    }

    /**
     * 前提：已持有 TestFlow，循环三次 getFrom。
     * 期望：parse 仅 1 次；mapper 未被调用。
     */
    @Test
    @DisplayName("子流重复 getFrom 只 parse 一次且不查库")
    void repeatedSubflowGetFrom_parsesOnceWithoutReload() {
        GraphJson a = cache.getFrom(subflow);
        GraphJson b = cache.getFrom(subflow);
        GraphJson c = cache.getFrom(subflow);
        assertNotNull(a);
        assertSame(a, b);
        assertSame(b, c);
        assertEquals(1, cache.getParseCount());
        assertEquals(0, cache.getLoadCount());
        verify(mapper, times(0)).selectTestFlowById(SUBFLOW_ID);
    }
}
