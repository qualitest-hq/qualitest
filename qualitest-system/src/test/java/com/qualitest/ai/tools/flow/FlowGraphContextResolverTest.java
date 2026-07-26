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
 * {@link FlowGraphContextResolver} 单元测试。
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

    @Test
    void resolve_missingGraphAndFlowId_returnsError() {
        FlowGraphContextResolver.ResolvedGraph resolved = resolver.resolve(Map.of(), context);
        assertFalse(resolved.isOk());
        assertTrue(resolved.errorJson().contains("error"));
        assertTrue(resolved.errorJson().contains("hint"));
    }

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
