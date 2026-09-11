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
import com.qualitest.ai.tools.FlowDesignToolNames;
import com.qualitest.ai.tools.FlowDesignToolsDefinitionService;
import com.qualitest.flow.model.*;
import org.junit.jupiter.api.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 测 TestFlowDesignAgent：测试流 AI 设计 Agent（空 prompt / submit patch / 纯答疑 / 失败可读异常 / 摘要刷新）。
 * 边界：全 Mock，不调用真实 LLM。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=TestFlowDesignAgentTest
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
        when(conversationService.loadFlowDesignClientIdMap(any())).thenReturn(new HashMap<>());
        when(toolsDefinitionService.loadToolsDefinition()).thenReturn(List.of());
        when(contextFactory.fromDesignRequest(any(), any(), any(), any(), any())).thenAnswer(inv -> {
            TestFlowDesignRequest req = inv.getArgument(0);
            FlowDesignSubmitCapture capture = inv.getArgument(1);
            return FlowDesignToolContext.builder()
                    .testProjectId(req.getTestProjectId())
                    .testFlowId(req.getTestFlowId())
                    .graphJson(req.getGraphJson())
                    .maxSearchApis(10)
                    .maxToolResultBytes(8192)
                    .submitCapture(capture)
                    .assetUpsertCapture(inv.getArgument(2))
                    .aiChatSessionId(inv.getArgument(3))
                    .flowDesignClientIdMap(inv.getArgument(4) != null
                            ? inv.getArgument(4)
                            : new HashMap<>())
                    .build();
        });
    }

    /**
     * 前提：prompt 为空。
     * 期望：抛 LlmClientException（含「设计描述」），不调用 AgentRunner。
     */
    @Test
    @Order(1)
    @DisplayName("空 prompt 抛异常且不调 Runner")
    void design_missingPrompt_throws() {
        TestFlowDesignRequest request = baseRequest();
        request.setPrompt("");

        LlmClientException ex = assertThrows(LlmClientException.class, () -> agent.design(request, USER_ID));
        assertTrue(ex.getMessage().contains("设计描述"));
        verifyNoInteractions(agentRunner);
    }

    /**
     * 前提：Agent 调用 submit 且校验通过。
     * 期望：返回 patch、validation.ok、explainOnly=false；落库并触发摘要刷新。
     */
    @Test
    @Order(2)
    @DisplayName("submit 成功返回 patch 并落库")
    void design_submitReturnsPatch_success() {
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
        verify(toolExecutor).executeTool(eq(FlowDesignToolNames.SUBMIT_ADD_HTTP_NODE.getId()), anyString(), any());
        verify(conversationService).appendAssistantMessage(
                eq(SESSION_ID), eq("登录链路"), contains("\"patchJson\""), eq(1001L), isNull());
        verify(summaryService).maybeRefreshSummaryAsync(eq(SESSION_ID), eq(1001L));
    }

    /**
     * 前提：Agent 仅返回自然语言，未调用 submit。
     * 期望：explainOnly=true，patch=null，不执行 submit。
     */
    @Test
    @Order(3)
    @DisplayName("仅自然语言时 explainOnly")
    void design_summaryOnly_explainOnly() {
        TestFlowDesignRequest request = baseRequest();
        request.setPrompt("解释一下这个流程");

        when(agentRunner.run(any())).thenReturn(AiAgentRunner.AgentRunResult.builder()
                .content("这是流程说明")
                .stepsUsed(1)
                .build());

        TestFlowDesignResult result = agent.design(request, USER_ID);

        assertTrue(result.isExplainOnly());
        assertNull(result.getPatch());
        verify(toolExecutor, never()).executeTool(eq(FlowDesignToolNames.SUBMIT_ADD_HTTP_NODE.getId()), anyString(), any());
    }

    /**
     * 前提：已 submit patch，但 Runner 因空 content / maxSteps 返回非 ok。
     * 期望：仍返回 patch，不因 isOk=false 整单失败。
     */
    @Test
    @Order(4)
    @DisplayName("空 content 仍返回已 submit 的 patch")
    void design_submitWithEmptyContent_success() {
        TestFlowDesignRequest request = baseRequest();
        FlowDesignPatch normalizedPatch = patchWithLoginNode();
        DesignValidationResult validation = DesignValidationResult.builder()
                .ok(true)
                .errors(List.of())
                .warnings(List.of())
                .build();

        when(toolExecutor.executeTool(eq(FlowDesignToolNames.SUBMIT_ADD_HTTP_NODE.getId()), anyString(), any()))
                .thenAnswer(inv -> {
                    var ctx = inv.getArgument(2, com.qualitest.ai.tools.FlowDesignToolContext.class);
                    ctx.getSubmitCapture().record(new FlowDesignPatchNormalizer.NormalizeResult(normalizedPatch, validation));
                    return "{\"received\":true}";
                });
        when(agentRunner.run(any())).thenAnswer(inv -> {
            inv.getArgument(0, AiAgentRunner.AgentRunOptions.class).getToolExecutor().execute(
                    FlowDesignToolNames.SUBMIT_ADD_HTTP_NODE.getId(),
                    "{\"id\":\"9001\",\"data\":{\"callMode\":\"project\",\"name\":\"登录\"}}");
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
    }

    /**
     * 前提：AgentRunner 返回 error=步数超限。
     * 期望：包装为 LlmClientException，消息为「步数超限」。
     */
    @Test
    @Order(5)
    @DisplayName("Agent 失败包装为可读异常")
    void design_agentFailure_throws() {
        when(agentRunner.run(any())).thenReturn(AiAgentRunner.AgentRunResult.builder()
                .error("步数超限")
                .stepsUsed(8)
                .build());

        LlmClientException ex = assertThrows(LlmClientException.class, () -> agent.design(baseRequest(), USER_ID));
        assertEquals("步数超限", ex.getMessage());
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
        when(toolExecutor.executeTool(eq(FlowDesignToolNames.SUBMIT_ADD_HTTP_NODE.getId()), anyString(), any()))
                .thenAnswer(inv -> {
                    var ctx = inv.getArgument(2, com.qualitest.ai.tools.FlowDesignToolContext.class);
                    ctx.getSubmitCapture().record(new FlowDesignPatchNormalizer.NormalizeResult(normalizedPatch, validation));
                    return "{\"received\":true}";
                });
        when(agentRunner.run(any())).thenAnswer(inv -> {
            AiAgentRunner.AgentRunOptions opts = inv.getArgument(0);
            opts.getToolExecutor().execute(
                    FlowDesignToolNames.SUBMIT_ADD_HTTP_NODE.getId(),
                    "{\"id\":\"9001\",\"data\":{\"callMode\":\"project\",\"name\":\"登录\"}}");
            return AiAgentRunner.AgentRunResult.builder()
                    .content(assistantText)
                    .stepsUsed(1)
                    .build();
        });
    }
}
