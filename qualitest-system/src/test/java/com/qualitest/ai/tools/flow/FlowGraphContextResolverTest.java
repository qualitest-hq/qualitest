package com.qualitest.ai.tools;

import com.qualitest.ai.tools.flow.FlowGraphContextResolver;
import com.qualitest.flow.model.GraphJson;
import com.qualitest.project.result.TestFlowResult;
import com.qualitest.project.service.ITestFlowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * 测 FlowGraphContextResolver：从参数或 testFlowId 解析图上下文。
 * 边界：Mock ITestFlowService，不访问真实库。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=FlowGraphContextResolverTest
 */
@ExtendWith(MockitoExtension.class)
class FlowGraphContextResolverTest {

    private static final Long PROJECT_ID = 100L;
    private static final Long FLOW_ID = 3001L;

    @Mock
    private ITestFlowService testFlowService;

    private FlowGraphContextResolver resolver;
    private FlowDesignToolContext context;

    @BeforeEach
    void setUp() {
        resolver = new FlowGraphContextResolver(testFlowService);
        context = FlowDesignToolContext.builder()
                .testProjectId(PROJECT_ID)
                .build();
    }

    /**
     * 前提：参数无 graph、无 testFlowId。
     * 期望：失败，错误 JSON 含 error 与 hint。
     */
    @Test
    void resolve_missingGraphAndFlowId_returnsError() {
        FlowGraphContextResolver.ResolvedGraph resolved = resolver.resolve(Map.of(), context);
        assertFalse(resolved.isOk());
        assertTrue(resolved.errorJson().contains("error"));
        assertTrue(resolved.errorJson().contains("hint"));
    }

    /**
     * 前提：仅传 testFlowId，Service 返回同项目的 graphJson。
     * 期望：解析成功，节点 n1 存在。
     */
    @Test
    void resolve_autoLoadByTestFlowId_returnsGraph() {
        when(testFlowService.selectTestFlowResult(FLOW_ID)).thenReturn(
                TestFlowResult.builder()
                        .testFlowId(FLOW_ID)
                        .testProjectId(PROJECT_ID)
                        .graphJson("{\"nodes\":[{\"id\":\"n1\",\"type\":\"http\"}],\"edges\":[]}")
                        .build());

        FlowGraphContextResolver.ResolvedGraph resolved = resolver.resolve(
                Map.of("testFlowId", "3001"), context);

        assertTrue(resolved.isOk());
        assertEquals(1, resolved.graph().getNodes().size());
        assertEquals("n1", resolved.graph().getNodes().get(0).getId());
    }

    /**
     * 前提：testFlowId 对应流属于其它项目。
     * 期望：失败（项目不匹配）。
     */
    @Test
    void resolve_wrongProject_returnsError() {
        when(testFlowService.selectTestFlowResult(FLOW_ID)).thenReturn(
                TestFlowResult.builder()
                        .testFlowId(FLOW_ID)
                        .testProjectId(999L)
                        .build());

        FlowGraphContextResolver.ResolvedGraph resolved = resolver.resolve(
                Map.of("testFlowId", "3001"), context);

        assertFalse(resolved.isOk());
        assertTrue(resolved.errorJson().contains("不属于当前项目"));
    }

    /**
     * 前提：上下文已带 graphJson，参数为空。
     * 期望：优先用上下文图，解析成功。
     */
    @Test
    void resolve_prefersContextGraphJson() {
        GraphJson graph = GraphJson.builder().build();
        FlowDesignToolContext ctx = FlowDesignToolContext.builder()
                .testProjectId(PROJECT_ID)
                .graphJson(graph)
                .build();

        FlowGraphContextResolver.ResolvedGraph resolved = resolver.resolve(Map.of(), ctx);
        assertTrue(resolved.isOk());
        assertEquals(graph, resolved.graph());
    }
}
