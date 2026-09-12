package com.qualitest.ai.llm;

import com.qualitest.ai.config.AiLlmConfigService;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 测 AiAgentRunner：多步 tool_call 循环（执行工具、写回 tool 消息、maxSteps / 终态探针）。
 * 边界：Mock LlmProvider / AiLlmConfigService，不发真实 LLM。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=AiAgentRunnerTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AiAgentRunnerTest {

    private LlmProvider llmProvider;
    private AiLlmConfigService configService;
    private AiAgentRunner runner;
    private LlmModelConfig modelConfig;

    @BeforeEach
    void setUp() {
        llmProvider = mock(LlmProvider.class);
        configService = mock(AiLlmConfigService.class);
        when(configService.getMaxSteps()).thenReturn(8);
        runner = new AiAgentRunner(llmProvider, configService);
        modelConfig = LlmModelConfig.builder()
                .aiLlmModelId(1001L)
                .modelName("gpt-test")
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
        LlmToolCall toolCall = LlmToolCall.builder()
                .id("call_1")
                .name("search_apis")
                .argumentsJson("{\"keyword\":\"login\"}")
                .build();
        when(llmProvider.chat(eq(modelConfig), any()))
                .thenReturn(LlmChatResponse.builder()
                        .toolCalls(List.of(toolCall))
                        .build())
                .thenReturn(LlmChatResponse.builder()
                        .content("{\"addNodes\":[],\"addEdges\":[]}")
                        .finishReason("stop")
                        .build());

        AiAgentRunner.AgentRunResult result = runner.run(AiAgentRunner.AgentRunOptions.builder()
                .modelConfig(modelConfig)
                .initialMessages(List.of(LlmMessage.user("plan")))
                .toolExecutor((name, args) -> "{\"items\":[]}")
                .maxSteps(3)
                .build());

        assertTrue(result.isOk());
        assertEquals("{\"addNodes\":[],\"addEdges\":[]}", result.getContent());
        assertEquals(1, result.getStepsUsed());
        verify(llmProvider, times(2)).chat(eq(modelConfig), any());
    }

    /**
     * 前提：LLM 连续返回 tool_call 直至触达 maxSteps=2。
     * 期望：ok=false，error 含「最大步数」，stepsUsed=2。
     */
    @Test
    @Order(2)
    @DisplayName("超过 maxSteps 时返回错误")
    void run_exceedsMaxSteps_returnsError() {
        LlmToolCall toolCall = LlmToolCall.builder()
                .id("call_loop")
                .name("search_apis")
                .argumentsJson("{}")
                .build();
        when(llmProvider.chat(eq(modelConfig), any()))
                .thenReturn(LlmChatResponse.builder().toolCalls(List.of(toolCall)).build());

        AiAgentRunner.AgentRunResult result = runner.run(AiAgentRunner.AgentRunOptions.builder()
                .modelConfig(modelConfig)
                .initialMessages(List.of(LlmMessage.user("plan")))
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
        LlmToolCall toolCall = LlmToolCall.builder()
                .id("call_loop")
                .name("search_apis")
                .argumentsJson("{}")
                .build();
        when(llmProvider.chat(eq(modelConfig), any()))
                .thenReturn(LlmChatResponse.builder().toolCalls(List.of(toolCall)).build());

        ArgumentCaptor<LlmChatRequest> requestCaptor = ArgumentCaptor.forClass(LlmChatRequest.class);
        AiAgentRunner.AgentRunResult result = runner.run(AiAgentRunner.AgentRunOptions.builder()
                .modelConfig(modelConfig)
                .initialMessages(List.of(LlmMessage.user("plan")))
                .toolExecutor((name, args) -> "{}")
                .maxSteps(3)
                .designSubmitNudgeEnabled(true)
                .build());

        verify(llmProvider, times(3)).chat(eq(modelConfig), requestCaptor.capture());
        List<LlmChatRequest> requests = requestCaptor.getAllValues();
        // 第 2、3 轮请求应已带上催 submit（第 1 轮 tool 后 steps=1，剩余 2）
        boolean secondHasNudge = requests.get(1).getMessages().stream()
                .anyMatch(m -> AiAgentRunner.SUBMIT_NUDGE_CONTENT.equals(m.getContent()));
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
        when(llmProvider.chat(eq(modelConfig), any()))
                .thenReturn(LlmChatResponse.builder()
                        .content("  {\"summary\":\"ok\"}  ")
                        .finishReason("stop")
                        .build());

        AiAgentRunner.AgentRunResult result = runner.run(AiAgentRunner.AgentRunOptions.builder()
                .modelConfig(modelConfig)
                .initialMessages(List.of(LlmMessage.system("sys"), LlmMessage.user("go")))
                .maxSteps(5)
                .build());

        assertTrue(result.isOk());
        assertEquals("{\"summary\":\"ok\"}", result.getContent());
        assertEquals(0, result.getStepsUsed());
        verify(llmProvider, times(1)).chat(eq(modelConfig), any());
    }

    /**
     * 前提：触达步数上限且无 content，但 terminalSuccessProbe=true。
     * 期望：仍 ok，terminalViaTool=true，content=null。
     */
    @Test
    @Order(4)
    @DisplayName("终态探针成功时无 content 仍 ok")
    void run_terminalSuccessProbe_succeedsWithoutContent() {
        LlmToolCall toolCall = LlmToolCall.builder()
                .id("call_submit")
                .name("submit_http_node")
                .argumentsJson("{}")
                .build();
        when(llmProvider.chat(eq(modelConfig), any()))
                .thenReturn(LlmChatResponse.builder().toolCalls(List.of(toolCall)).build());

        AiAgentRunner.AgentRunResult result = runner.run(AiAgentRunner.AgentRunOptions.builder()
                .modelConfig(modelConfig)
                .initialMessages(List.of(LlmMessage.user("plan")))
                .toolExecutor((name, args) -> "{\"received\":true}")
                .maxSteps(1)
                .terminalSuccessProbe(() -> true)
                .build());

        assertTrue(result.isOk());
        assertTrue(result.isTerminalViaTool());
        assertNull(result.getContent());
        assertEquals(1, result.getStepsUsed());
    }
}
