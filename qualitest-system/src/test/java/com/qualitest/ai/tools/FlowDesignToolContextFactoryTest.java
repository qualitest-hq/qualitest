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
 * {@link FlowDesignToolContextFactory} 单元测试。
 * <p>
 * 验证 Web 设计请求与 MCP 调用请求均能组装出完整的 {@link FlowDesignToolContext}，
 * 含项目归属校验、scopeApiIds 解析及运行时限制参数注入。
 * ConfigService 使用 Mock。
 * <p>
 * 运行（qualitest 目录）：mvn test -pl qualitest-system -am -DskipTests=false -Dtest=FlowDesignToolContextFactoryTest
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
     * Web {@link TestFlowDesignRequest} 应映射项目、测试流、画布及配置上限。
     * 期望：testProjectId=PROJECT_ID，testFlowId=3001L，maxSearchApis=10。
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
     * MCP 请求省略 testProjectId 时，应回退使用 Token 解析出的项目 id。
     * 期望：scopeApiIds 字符串列表解析为 Long 列表。
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
     * MCP 请求显式传入的 testProjectId 与 Token 归属项目不匹配。
     * 期望：抛出 {@link ServiceException}，拒绝越权访问。
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
