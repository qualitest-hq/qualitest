package com.qualitest.ai.scenario.flow;

import com.qualitest.ai.config.AiLlmConfigService;
import com.qualitest.ai.domain.AiChatSession;
import com.qualitest.ai.llm.*;
import com.qualitest.ai.llm.history.HistoryWindowPolicy;
import com.qualitest.ai.llm.history.HistoryWindowPolicyResolver;
import com.qualitest.ai.scenario.flow.model.FlowDesignPatch;
import com.qualitest.ai.scenario.flow.model.DesignValidationResult;
import com.qualitest.ai.scenario.flow.model.TestFlowDesignRequest;
import com.qualitest.ai.scenario.flow.model.TestFlowDesignResult;
import com.qualitest.ai.service.AiChatConversationService;
import com.qualitest.ai.service.AiChatSessionSummaryService;
import com.qualitest.ai.service.IAiLlmModelService;
import com.qualitest.ai.tools.FlowDesignSubmitCapture;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.FlowDesignToolContextFactory;
import com.qualitest.ai.tools.FlowDesignToolExecutor;
import com.qualitest.ai.tools.FlowDesignToolsDefinitionService;
import com.qualitest.flow.model.*;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.qualitest.flow.support.FlowTestSections.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 测试流 AI 设计 Agent 单元测试。
 *
 * 验证：prompt 为空时拒绝请求；Agent 调用 submit 后返回 patch；
 * 纯答疑轮次 explainOnly=true；Agent 失败时抛出可读异常；
 * 成功落库 assistant 消息后触发异步会话摘要刷新。
 * 依赖 Mock，不调用真实 LLM。
 *
 * 运行：mvn test -pl qualitest-system -am -DskipTests=false -Dtest=TestFlowDesignAgentTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TestFlowDesignAgentTest {

    private static final Long USER_ID = 1L;
    private static final Long SESSION_ID = 90001L;

    private IAiLlmModelService modelService;
    private AiLlmConfigService configService;
    private AiAgentRunner agentRunner;
    private FlowDesignToolExecutor toolExecutor;
    private FlowDesignToolContextFactory contextFactory;
    private FlowDesignToolsDefinitionService toolsDefinitionService;
    private AiChatConversationService conversationService;
    private HistoryWindowPolicyResolver historyWindowPolicyResolver;
    private AiChatSessionSummaryService summaryService;
    private TestFlowDesignAgent agent;

    private LlmModelConfig modelConfig;
    private AiChatSession session;

    @BeforeEach
    void setUp() {
        modelService = mock(IAiLlmModelService.class);
        configService = mock(AiLlmConfigService.class);
        agentRunner = mock(AiAgentRunner.class);
        toolExecutor = mock(FlowDesignToolExecutor.class);
        contextFactory = mock(FlowDesignToolContextFactory.class);
        toolsDefinitionService = mock(FlowDesignToolsDefinitionService.class);
        conversationService = mock(AiChatConversationService.class);
        historyWindowPolicyResolver = mock(HistoryWindowPolicyResolver.class);
        summaryService = mock(AiChatSessionSummaryService.class);
        agent = new TestFlowDesignAgent(
                modelService, configService, agentRunner, toolExecutor,
                contextFactory, toolsDefinitionService, conversationService,
                historyWindowPolicyResolver, summaryService);

        modelConfig = LlmModelConfig.builder()
                .aiLlmModelId(1001L)
                .aiLlmVendorId(2001L)
                .modelName("gpt-test")
                .vendorName("TestVendor")
                .baseUrl("https://example.com/v1")
                .apiKey("sk-test")
                .build();

        session = AiChatSession.builder()
                .aiChatSessionId(SESSION_ID)
                .userId(USER_ID)
                .build();

        when(configService.getMaxSearchApis()).thenReturn(10);
        when(configService.getMaxToolResultBytes()).thenReturn(8192);
        when(configService.getMaxSteps()).thenReturn(8);
        when(modelService.resolve(1001L)).thenReturn(modelConfig);
        when(conversationService.loadOrCreate(any(), anyString(), anyLong(), anyString(), eq(USER_ID), anyLong(), anyString(), any()))
                .thenReturn(session);
        when(historyWindowPolicyResolver.resolve(any(LlmModelConfig.class)))
                .thenReturn(HistoryWindowPolicy.builder().countLimit(10).tokenBudget(12000).build());
        when(conversationService.loadMessagesForLlm(eq(SESSION_ID), any(LlmModelConfig.class), any(), anyInt()))
                .thenReturn(List.of());
        when(toolsDefinitionService.loadToolsDefinition()).thenReturn(List.of());
        when(contextFactory.fromDesignRequest(any(), any())).thenAnswer(inv -> {
            TestFlowDesignRequest req = inv.getArgument(0);
            FlowDesignSubmitCapture capture = inv.getArgument(1);
            return FlowDesignToolContext.builder()
                    .testProjectId(req.getTestProjectId())
                    .testFlowId(req.getTestFlowId())
                    .graphJson(req.getGraphJson())
                    .maxSearchApis(10)
                    .maxToolResultBytes(8192)
                    .submitCapture(capture)
                    .build();
        });
    }

    /**
     * prompt 为空时。
     * 期望：抛出 LlmClientException，不调用 AgentRunner。
     */
    @Test
    @Order(1)
    void design_missingPrompt_throws() {
        begin("design_missingPrompt_throws");
        TestFlowDesignRequest request = baseRequest();
        request.setPrompt("");

        LlmClientException ex = assertThrows(LlmClientException.class, () -> agent.design(request, USER_ID));
        assertTrue(ex.getMessage().contains("设计描述"));
        verifyNoInteractions(agentRunner);
        end("design_missingPrompt_throws");
    }

    /**
     * Agent 调用 submit 且校验通过时，应返回 patch、validation 与 explainOnly=false。
     * 期望：写入会话消息、触发异步摘要刷新，并执行 submit 工具。
     */
    @Test
    @Order(2)
    void design_submitReturnsPatch_success() {
        begin("design_submitReturnsPatch_success");
        TestFlowDesignRequest request = baseRequest();
        FlowDesignPatch rawPatch = patchWithLoginNode();
        FlowDesignPatch normalizedPatch = patchWithLoginNode();
        DesignValidationResult validation = DesignValidationResult.builder()
                .ok(true)
                .errors(List.of())
                .warnings(List.of())
                .build();

        stubAgentRunWithSubmit(rawPatch, normalizedPatch, validation, "已建议新增登录节点");

        TestFlowDesignResult result = agent.design(request, USER_ID);

        assertEquals(1001L, result.getAiLlmModelId());
        assertEquals("登录链路", result.getSummary());
        assertNotNull(result.getPatch());
        assertTrue(result.getValidation().isOk());
        assertFalse(result.isExplainOnly());
        assertEquals(SESSION_ID, result.getAiChatSessionId());
        verify(agentRunner).run(any());
        verify(toolExecutor).executeTool(eq(FlowDesignToolExecutor.SUBMIT_FLOW_DESIGN_PATCH), anyString(), any());
        verify(conversationService).appendAssistantMessage(
                eq(SESSION_ID), eq("登录链路"), contains("\"patchJson\""), eq(1001L), isNull());
        verify(summaryService).maybeRefreshSummaryAsync(eq(SESSION_ID), eq(1001L));
        end("design_submitReturnsPatch_success");
    }

    /**
     * Agent 仅返回自然语言说明、未调用 submit 时。
     * 期望：explainOnly=true，patch 为 null，不执行 submit 工具。
     */
    @Test
    @Order(3)
    void design_summaryOnly_explainOnly() {
        begin("design_summaryOnly_explainOnly");
        TestFlowDesignRequest request = baseRequest();
        request.setPrompt("解释一下这个流程");

        when(agentRunner.run(any())).thenReturn(AiAgentRunner.AgentRunResult.builder()
                .content("这是流程说明")
                .stepsUsed(1)
                .build());

        TestFlowDesignResult result = agent.design(request, USER_ID);

        assertTrue(result.isExplainOnly());
        assertNull(result.getPatch());
        verify(toolExecutor, never()).executeTool(eq(FlowDesignToolExecutor.SUBMIT_FLOW_DESIGN_PATCH), anyString(), any());
        end("design_summaryOnly_explainOnly");
    }

    /**
     * Agent 已 submit patch 但 Runner 因空 content 返回 error（如触达 maxSteps）时。
     * 期望：仍返回 patch，不因 isOk=false 失败。
     */
    @Test
    @Order(4)
    void design_submitWithEmptyContent_success() {
        begin("design_submitWithEmptyContent_success");
        TestFlowDesignRequest request = baseRequest();
        FlowDesignPatch normalizedPatch = patchWithLoginNode();
        DesignValidationResult validation = DesignValidationResult.builder()
                .ok(true)
                .errors(List.of())
                .warnings(List.of())
                .build();

        when(toolExecutor.executeTool(eq(FlowDesignToolExecutor.SUBMIT_FLOW_DESIGN_PATCH), anyString(), any()))
                .thenAnswer(inv -> {
                    var ctx = inv.getArgument(2, com.qualitest.ai.tools.FlowDesignToolContext.class);
                    ctx.getSubmitCapture().record(new FlowDesignPatchNormalizer.NormalizeResult(normalizedPatch, validation));
                    return "{\"received\":true}";
                });
        when(agentRunner.run(any())).thenAnswer(inv -> {
            inv.getArgument(0, AiAgentRunner.AgentRunOptions.class).getToolExecutor().execute(
                    FlowDesignToolExecutor.SUBMIT_FLOW_DESIGN_PATCH,
                    "{\"summary\":\"登录链路\",\"addNodes\":[{\"id\":\"9001\",\"type\":\"http\"}],\"addEdges\":[]}");
            return AiAgentRunner.AgentRunResult.builder()
                    .terminalViaTool(true)
                    .stepsUsed(8)
                    .build();
        });

        TestFlowDesignResult result = agent.design(request, USER_ID);

        assertFalse(result.isExplainOnly());
        assertNotNull(result.getPatch());
        assertEquals("登录链路", result.getSummary());
        verify(conversationService).appendAssistantMessage(
                eq(SESSION_ID), eq("登录链路"), contains("\"patchJson\""), eq(1001L), isNull());
        end("design_submitWithEmptyContent_success");
    }

    /**
     * AgentRunner 返回 error 时。
     * 期望：将错误信息包装为 LlmClientException 抛出。
     */
    @Test
    @Order(5)
    void design_agentFailure_throws() {
        begin("design_agentFailure_throws");
        when(agentRunner.run(any())).thenReturn(AiAgentRunner.AgentRunResult.builder()
                .error("步数超限")
                .stepsUsed(8)
                .build());

        LlmClientException ex = assertThrows(LlmClientException.class, () -> agent.design(baseRequest(), USER_ID));
        assertEquals("步数超限", ex.getMessage());
        end("design_agentFailure_throws");
    }

    private static TestFlowDesignRequest baseRequest() {
        GraphRunScenario scenario = GraphRunScenario.builder()
                .id("2042000000000000101")
                .name("默认")
                .testProjectEnvId("")
                .flowSeed(new HashMap<>())
                .build();
        GraphJson graph = GraphJson.builder()
                .nodes(new ArrayList<>())
                .edges(new ArrayList<>())
                .meta(GraphMeta.builder()
                        .activeScenarioId("2042000000000000101")
                        .scenarios(List.of(scenario))
                        .flowOutputs(new ArrayList<>())
                        .build())
                .build();

        TestFlowDesignRequest request = new TestFlowDesignRequest();
        request.setTestProjectId(100L);
        request.setTestFlowId(3001L);
        request.setAiLlmModelId(1001L);
        request.setPrompt("生成登录测试");
        request.setGraphJson(graph);
        return request;
    }

    private static FlowDesignPatch patchWithLoginNode() {
        FlowDesignPatch patch = new FlowDesignPatch();
        patch.setSummary("登录链路");
        patch.setAddNodes(new ArrayList<>(List.of(
                GraphNode.builder()
                        .id("9001")
                        .type("http")
                        .position(GraphNodePosition.builder().x(40).y(80).build())
                        .data(new HashMap<>(Map.of("name", "登录")))
                        .build()
        )));
        return patch;
    }

    /** 模拟 Agent 循环：触发 submit 工具并填充 capture，再返回自然语言结束文本 */
    private void stubAgentRunWithSubmit(FlowDesignPatch rawPatch,
                                        FlowDesignPatch normalizedPatch,
                                        DesignValidationResult validation,
                                        String assistantText) {
        when(toolExecutor.executeTool(eq(FlowDesignToolExecutor.SUBMIT_FLOW_DESIGN_PATCH), anyString(), any()))
                .thenAnswer(inv -> {
                    var ctx = inv.getArgument(2, com.qualitest.ai.tools.FlowDesignToolContext.class);
                    ctx.getSubmitCapture().record(new FlowDesignPatchNormalizer.NormalizeResult(normalizedPatch, validation));
                    return "{\"received\":true}";
                });
        when(agentRunner.run(any())).thenAnswer(inv -> {
            AiAgentRunner.AgentRunOptions opts = inv.getArgument(0);
            opts.getToolExecutor().execute(
                    FlowDesignToolExecutor.SUBMIT_FLOW_DESIGN_PATCH,
                    "{\"summary\":\"登录链路\",\"addNodes\":[{\"id\":\"9001\",\"type\":\"http\"}],\"addEdges\":[]}");
            return AiAgentRunner.AgentRunResult.builder()
                    .content(assistantText)
                    .stepsUsed(1)
                    .build();
        });
    }
}
