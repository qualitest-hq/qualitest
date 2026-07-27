package com.qualitest.ai.tools;

import com.qualitest.ai.config.AiLlmConfigService;
import com.qualitest.ai.scenario.flow.model.TestFlowDesignRequest;
import com.qualitest.api.params.McpToolInvokeParams;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.flow.model.GraphJson;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.List;

import static com.qualitest.flow.support.FlowTestSections.begin;
import static com.qualitest.flow.support.FlowTestSections.end;
import static com.qualitest.flow.support.FlowTestSections.log;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 测 FlowDesignToolContextFactory：Web 设计请求与 MCP 调用组装 FlowDesignToolContext。
 * 边界：Mock AiLlmConfigService；校验项目归属与 scopeApiIds。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=FlowDesignToolContextFactoryTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlowDesignToolContextFactoryTest {

    private static final Long PROJECT_ID = 100L;
    private static final Long TOKEN_PROJECT_ID = 100L;

    private AiLlmConfigService configService;
    private FlowDesignToolContextFactory factory;

    @BeforeEach
    void setUp() {
        configService = mock(AiLlmConfigService.class);
        when(configService.getMaxSearchApis()).thenReturn(10);
        when(configService.getMaxToolResultBytes()).thenReturn(8192);
        factory = new FlowDesignToolContextFactory(configService);
    }

    /**
     * 前提：Web TestFlowDesignRequest 带项目、流 id 与空图。
     * 期望：上下文映射项目/流 id，并注入 maxSearchApis=10、maxToolResultBytes=8192。
     */
    @Test
    @Order(1)
    void fromDesignRequest_buildsContextWithMentions() {
        begin("fromDesignRequest_buildsContextWithMentions");
        TestFlowDesignRequest request = new TestFlowDesignRequest();
        request.setTestProjectId(PROJECT_ID);
        request.setTestFlowId(3001L);
        request.setGraphJson(GraphJson.builder().build());

        FlowDesignToolContext ctx = factory.fromDesignRequest(request, new FlowDesignSubmitCapture());

        assertEquals(PROJECT_ID, ctx.getTestProjectId());
        assertEquals(3001L, ctx.getTestFlowId());
        assertEquals(10, ctx.getMaxSearchApis());
        assertEquals(8192, ctx.getMaxToolResultBytes());
        log("testFlowId=3001 maxSearchApis=10");
        end("fromDesignRequest_buildsContextWithMentions");
    }

    /**
     * 前提：MCP 请求省略 testProjectId，仅有 Token 项目与 scopeApiIds=["2001"]。
     * 期望：回退 Token 项目；scopeApiIds 解析为 Long 2001。
     */
    @Test
    @Order(2)
    void fromMcpRequest_usesTokenProjectWhenOmitted() {
        begin("fromMcpRequest_usesTokenProjectWhenOmitted");
        McpToolInvokeParams params = new McpToolInvokeParams();
        params.setTestFlowId(3001L);
        params.setScopeApiIds(List.of("2001"));

        FlowDesignToolContext ctx = factory.fromMcpRequest(params, TOKEN_PROJECT_ID, null);

        assertEquals(TOKEN_PROJECT_ID, ctx.getTestProjectId());
        assertEquals(3001L, ctx.getTestFlowId());
        assertNotNull(ctx.getScopeApiIds());
        assertEquals(2001L, ctx.getScopeApiIds().get(0));
        log("scopeApiId=2001");
        end("fromMcpRequest_usesTokenProjectWhenOmitted");
    }

    /**
     * 前提：MCP 显式 testProjectId=999，与 Token 项目 100 不一致。
     * 期望：抛 ServiceException，消息含「不一致」。
     */
    @Test
    @Order(3)
    void fromMcpRequest_rejectsMismatchedProjectId() {
        begin("fromMcpRequest_rejectsMismatchedProjectId");
        McpToolInvokeParams params = new McpToolInvokeParams();
        params.setTestProjectId(999L);

        ServiceException ex = assertThrows(ServiceException.class,
                () -> factory.fromMcpRequest(params, TOKEN_PROJECT_ID, null));
        assertTrue(ex.getMessage().contains("不一致"));
        log("rejected=true");
        end("fromMcpRequest_rejectsMismatchedProjectId");
    }
}
