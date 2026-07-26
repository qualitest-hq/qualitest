package com.qualitest.ai.llm;

import com.qualitest.ai.config.AiLlmConfigService;
import org.junit.jupiter.api.*;

import java.util.List;

import static com.qualitest.flow.support.FlowTestSections.begin;
import static com.qualitest.flow.support.FlowTestSections.end;
import static com.qualitest.flow.support.FlowTestSections.log;
import static com.qualitest.flow.support.FlowTestSections.quote;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * {@link AiAgentRunner} 单元测试：验证 LLM Agent 多步工具调用循环。
 * <p>
 * 被测对象维护 messages 历史，循环调用 LlmProvider.chat：若响应含 tool_calls 则执行 toolExecutor、
 * 将结果追加为 tool 消息后继续；若响应含 content 则结束。受 maxSteps 限制，超限返回 error。
 * <p>
 * 运行（qualitest 目录）：mvn test -pl qualitest-system -am -DskipTests=false -Dtest=AiAgentRunnerTest
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
     * 典型 Agent 流程：第 1 轮 LLM 返回 tool_call → 执行工具 → 第 2 轮返回 JSON content。
     * 期望：ok=true，content 为最终 JSON，stepsUsed=1，共调用 LLM 2 次。
     */
    @Test
    @Order(1)
    void run_toolCallThenFinalJson_succeeds() {
        begin("run_toolCallThenFinalJson_succeeds");
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
        log("ok=" + result.isOk() + " content=" + quote(result.getContent()) + " stepsUsed=" + result.getStepsUsed());
        end("run_toolCallThenFinalJson_succeeds");
    }

    /**
     * LLM 连续返回 tool_call 直到达到 maxSteps 上限。
     * 期望：ok=false，error 含「最大步数」，stepsUsed=maxSteps。
     */
    @Test
    @Order(2)
    void run_exceedsMaxSteps_returnsError() {
        begin("run_exceedsMaxSteps_returnsError");
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
        log("ok=" + result.isOk() + " error=" + quote(result.getError()) + " stepsUsed=" + result.getStepsUsed());
        end("run_exceedsMaxSteps_returnsError");
    }

    /**
     * 首轮 LLM 直接返回 content（无 tool_calls）时无需工具步。
     * 期望：ok=true，content trim 后返回，stepsUsed=0，仅调用 LLM 1 次。
     */
    @Test
    @Order(3)
    void run_directContent_noToolCalls() {
        begin("run_directContent_noToolCalls");
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
        log("ok=" + result.isOk() + " content=" + quote(result.getContent()) + " stepsUsed=" + result.getStepsUsed());
        end("run_directContent_noToolCalls");
    }

    /**
     * terminalSuccessProbe 为 true 时，即使无 content 且触达步数上限也视为成功。
     */
    @Test
    @Order(4)
    void run_terminalSuccessProbe_succeedsWithoutContent() {
        begin("run_terminalSuccessProbe_succeedsWithoutContent");
        LlmToolCall toolCall = LlmToolCall.builder()
                .id("call_submit")
                .name("submit_flow_design_patch")
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
        end("run_terminalSuccessProbe_succeedsWithoutContent");
    }
}
