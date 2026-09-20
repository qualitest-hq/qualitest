package com.qualitest.ai.llm;

import com.qualitest.ai.config.AiLlmConfigService;
import com.qualitest.ai.llm.lc4j.Lc4jClientFactory;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Agent 工具循环单测。
 * 覆盖：多步工具调用、结果写回对话、步数上限、终态探测。
 * 使用 Mock 客户端与配置，不访问真实大模型。
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AiAgentRunnerTest {

    private Lc4jClientFactory lc4jClientFactory;
    private ChatModel chatModel;
    private AiLlmConfigService configService;
    private AiAgentRunner runner;
    private LlmModelConfig modelConfig;

    @BeforeEach
    void setUp() {
        lc4jClientFactory = mock(Lc4jClientFactory.class);
        chatModel = mock(ChatModel.class);
        configService = mock(AiLlmConfigService.class);
        when(configService.getMaxSteps()).thenReturn(8);
        when(configService.getReadTimeoutMs()).thenReturn(120_000);
        when(lc4jClientFactory.chatModel(any(), anyBoolean())).thenReturn(chatModel);
        runner = new AiAgentRunner(lc4jClientFactory, configService);
        modelConfig = LlmModelConfig.builder()
                .aiLlmModelId(1001L)
                .modelName("gpt-test")
                .provider(LlmProviderTypes.OPENAI_COMPATIBLE)
                .baseUrl("https://example.com/v1")
                .apiKey("sk-test")
                .build();
    }

    /**
     * 前提：第 1 轮返回 tool_call，第 2 轮返回 JSON content。
     * 期望：ok=true，content 为最终 JSON，stepsUsed=1，LLM 调用 2 次。
     */
    @Test
    @Order(1)
    @DisplayName("tool_call 后返回最终 JSON 成功")
    void run_toolCallThenFinalJson_succeeds() {
        ToolExecutionRequest toolCall = ToolExecutionRequest.builder()
                .id("call_1")
                .name("search_apis")
                .arguments("{\"keyword\":\"login\"}")
                .build();
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder()
                        .aiMessage(AiMessage.builder().toolExecutionRequests(List.of(toolCall)).build())
                        .build())
                .thenReturn(ChatResponse.builder()
                        .aiMessage(AiMessage.from("{\"addNodes\":[],\"addEdges\":[]}"))
                        .build());

        AiAgentRunner.AgentRunResult result = runner.run(AiAgentRunner.AgentRunOptions.builder()
                .modelConfig(modelConfig)
                .initialMessages(List.of(UserMessage.from("plan")))
                .toolExecutor((name, args) -> "{\"items\":[]}")
                .maxSteps(3)
                .build());

        assertTrue(result.isOk());
        assertEquals("{\"addNodes\":[],\"addEdges\":[]}", result.getContent());
        assertEquals(1, result.getStepsUsed());
        assertNotNull(result.getToolTrace());
        assertEquals(1, result.getToolTrace().getJSONArray("calls").size());
        assertEquals("search_apis", result.getToolTrace().getJSONArray("calls").getJSONObject(0).getString("name"));
        assertTrue(result.getToolTrace().getJSONArray("calls").getJSONObject(0).getBooleanValue("ok"));
        assertEquals("login", result.getToolTrace().getJSONArray("calls").getJSONObject(0)
                .getJSONObject("args").getString("keyword"));
        verify(chatModel, times(2)).chat(any(ChatRequest.class));
    }

    /**
     * 前提：LLM 连续返回 tool_call 直至触达 maxSteps=2。
     * 期望：ok=false，error 含「最大步数」，stepsUsed=2。
     */
    @Test
    @Order(2)
    @DisplayName("超过 maxSteps 时返回错误")
    void run_exceedsMaxSteps_returnsError() {
        ToolExecutionRequest toolCall = ToolExecutionRequest.builder()
                .id("call_loop")
                .name("search_apis")
                .arguments("{}")
                .build();
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder()
                        .aiMessage(AiMessage.builder().toolExecutionRequests(List.of(toolCall)).build())
                        .build());

        AiAgentRunner.AgentRunResult result = runner.run(AiAgentRunner.AgentRunOptions.builder()
                .modelConfig(modelConfig)
                .initialMessages(List.of(UserMessage.from("plan")))
                .toolExecutor((name, args) -> "{}")
                .maxSteps(2)
                .build());

        assertFalse(result.isOk());
        assertTrue(result.getError().contains("最大步数"));
        assertEquals(2, result.getStepsUsed());
    }

    /**
     * 前提：maxSteps=3，连续 tool_call，终态探针始终 false。
     * 期望：第 2 轮 LLM 请求的 messages 末尾含催 submit 文案；达上限错误亦提示 submit。
     */
    @Test
    @Order(5)
    @DisplayName("步数将尽时注入催 submit 提示")
    void run_nearMaxSteps_appendsSubmitNudge() {
        ToolExecutionRequest toolCall = ToolExecutionRequest.builder()
                .id("call_loop")
                .name("search_apis")
                .arguments("{}")
                .build();
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder()
                        .aiMessage(AiMessage.builder().toolExecutionRequests(List.of(toolCall)).build())
                        .build());

        ArgumentCaptor<ChatRequest> requestCaptor = ArgumentCaptor.forClass(ChatRequest.class);
        AiAgentRunner.AgentRunResult result = runner.run(AiAgentRunner.AgentRunOptions.builder()
                .modelConfig(modelConfig)
                .initialMessages(List.of(UserMessage.from("plan")))
                .toolExecutor((name, args) -> "{}")
                .maxSteps(3)
                .designSubmitNudgeEnabled(true)
                .build());

        verify(chatModel, times(3)).chat(requestCaptor.capture());
        List<ChatRequest> requests = requestCaptor.getAllValues();
        boolean secondHasNudge = requests.get(1).messages().stream()
                .anyMatch(m -> m instanceof UserMessage user
                        && user.hasSingleText()
                        && AiAgentRunner.SUBMIT_NUDGE_CONTENT.equals(user.singleText()));
        assertTrue(secondHasNudge);
        assertFalse(result.isOk());
        assertTrue(result.getError().contains("submit_"));
    }

    /**
     * 前提：首轮直接返回 content，无 tool_calls。
     * 期望：ok=true，content 已 trim，stepsUsed=0，仅调 LLM 1 次。
     */
    @Test
    @Order(3)
    @DisplayName("无 tool_call 时直接返回 content")
    void run_directContent_noToolCalls() {
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder()
                        .aiMessage(AiMessage.from("  {\"summary\":\"ok\"}  "))
                        .build());

        AiAgentRunner.AgentRunResult result = runner.run(AiAgentRunner.AgentRunOptions.builder()
                .modelConfig(modelConfig)
                .initialMessages(List.of(
                        dev.langchain4j.data.message.SystemMessage.from("sys"),
                        UserMessage.from("go")))
                .maxSteps(5)
                .build());

        assertTrue(result.isOk());
        assertEquals("{\"summary\":\"ok\"}", result.getContent());
        assertEquals(0, result.getStepsUsed());
        verify(chatModel, times(1)).chat(any(ChatRequest.class));
    }

    /**
     * 前提：触达步数上限且无 content，但 terminalSuccessProbe=true。
     * 期望：仍 ok，terminalViaTool=true，content=null。
     */
    @Test
    @Order(4)
    @DisplayName("终态探针成功时无 content 仍 ok")
    void run_terminalSuccessProbe_succeedsWithoutContent() {
        ToolExecutionRequest toolCall = ToolExecutionRequest.builder()
                .id("call_submit")
                .name("submit_http_node")
                .arguments("{}")
                .build();
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder()
                        .aiMessage(AiMessage.builder().toolExecutionRequests(List.of(toolCall)).build())
                        .build());

        AiAgentRunner.AgentRunResult result = runner.run(AiAgentRunner.AgentRunOptions.builder()
                .modelConfig(modelConfig)
                .initialMessages(List.of(UserMessage.from("plan")))
                .toolExecutor((name, args) -> "{\"received\":true}")
                .maxSteps(1)
                .terminalSuccessProbe(() -> true)
                .build());

        assertTrue(result.isOk());
        assertTrue(result.isTerminalViaTool());
        assertNull(result.getContent());
        assertEquals(1, result.getStepsUsed());
    }

    /**
     * 前提：第 1 轮执行完 tool 后 cancelled=true。
     * 期望：interrupted=true，不再调下一轮 LLM，toolTrace 保留已执行工具。
     */
    @Test
    @Order(6)
    @DisplayName("取消标志置位后步间停止并保留 toolTrace")
    void run_cancelledAfterTool_returnsInterruptedWithTrace() {
        java.util.concurrent.atomic.AtomicBoolean cancelled = new java.util.concurrent.atomic.AtomicBoolean(false);
        ToolExecutionRequest toolCall = ToolExecutionRequest.builder()
                .id("call_1")
                .name("search_apis")
                .arguments("{\"keyword\":\"login\"}")
                .build();
        when(chatModel.chat(any(ChatRequest.class)))
                .thenReturn(ChatResponse.builder()
                        .aiMessage(AiMessage.builder().toolExecutionRequests(List.of(toolCall)).build())
                        .build());

        AiAgentRunner.AgentRunResult result = runner.run(AiAgentRunner.AgentRunOptions.builder()
                .modelConfig(modelConfig)
                .initialMessages(List.of(UserMessage.from("plan")))
                .toolExecutor((name, args) -> {
                    cancelled.set(true);
                    return "{\"items\":[]}";
                })
                .maxSteps(5)
                .cancelled(cancelled::get)
                .build());

        assertTrue(result.isInterrupted());
        assertFalse(result.isOk());
        assertEquals(AiAgentRunner.INTERRUPTED_MESSAGE, result.getError());
        assertEquals(1, result.getStepsUsed());
        assertNotNull(result.getToolTrace());
        assertEquals(1, result.getToolTrace().getJSONArray("calls").size());
        assertEquals("search_apis", result.getToolTrace().getJSONArray("calls").getJSONObject(0).getString("name"));
        verify(chatModel, times(1)).chat(any(ChatRequest.class));
    }

    /**
     * 前提：content / error / 占位 INTERRUPTED_MESSAGE 组合。
     * 期望：优先 content，其次非占位 error，否则「本轮已中断」。
     */
    @Test
    @Order(7)
    @DisplayName("中断 summary 决议口径")
    void resolveInterruptedSummary_prefersContentThenError() {
        assertEquals("已写出一半", AiAgentRunner.resolveInterruptedSummary("已写出一半", "x"));
        assertEquals("步数超限", AiAgentRunner.resolveInterruptedSummary(null, "步数超限"));
        assertEquals(AiAgentRunner.INTERRUPTED_MESSAGE,
                AiAgentRunner.resolveInterruptedSummary(null, AiAgentRunner.INTERRUPTED_MESSAGE));
        assertEquals(AiAgentRunner.INTERRUPTED_MESSAGE,
                AiAgentRunner.resolveInterruptedSummary("  ", null));
    }
}
