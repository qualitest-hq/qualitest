package com.qualitest.ai.llm;

import com.qualitest.ai.tools.FlowDesignToolSupport;
import com.qualitest.ai.config.AiLlmConfigService;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * 通用 Agent 循环执行器。
 * <p>
 * 反复调用 {@link LlmProvider#chat}，处理 tool 调用闭环，直到模型返回最终文本或达到步数上限。
 * <ul>
 *   <li>模型返回 toolCalls → 执行工具 → 追加 tool 消息 → 继续下一轮</li>
 *   <li>模型返回非空文本 → 视为最终答案并结束</li>
 *   <li>超出 maxSteps → 返回可读错误信息</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class AiAgentRunner {

    private final LlmProvider llmProvider;
    private final AiLlmConfigService aiLlmConfigService;

    /**
     * 执行 Agent 主循环。
     *
     * @param options 模型配置、初始消息、工具定义、工具执行器与步数上限
     * @return 成功时 content 为模型最终输出；失败时 error 为可读原因
     */
    public AgentRunResult run(AgentRunOptions options) {
        List<LlmMessage> messages = new ArrayList<>(options.getInitialMessages());
        int maxSteps = options.getMaxSteps() > 0 ? options.getMaxSteps() : aiLlmConfigService.getMaxSteps();
        AgentRunListener listener = options.getListener();
        StringBuilder thinkingAccumulator = new StringBuilder();
        int steps = 0;
        while (steps < maxSteps) {
            LlmChatRequest request = buildChatRequest(options, messages);
            LlmChatResponse response = listener != null
                    ? chatStreamCollect(options.getModelConfig(), request, listener, thinkingAccumulator)
                    : llmProvider.chat(options.getModelConfig(), request);
            if (response == null) {
                return AgentRunResult.builder()
                        .error("LLM 流式响应未完成")
                        .stepsUsed(steps)
                        .build();
            }
            appendThinking(thinkingAccumulator, response.getThinkingContent());
            if (response.getToolCalls() != null && !response.getToolCalls().isEmpty()) {
                messages.add(LlmMessage.assistant(response.getContent(), response.getToolCalls()));
                for (LlmToolCall tc : response.getToolCalls()) {
                    if (listener != null) {
                        listener.onToolStart(tc.getName());
                    }
                    String result = options.getToolExecutor().execute(tc.getName(), tc.getArgumentsJson());
                    if (listener != null) {
                        listener.onToolEnd(tc.getName());
                    }
                    messages.add(LlmMessage.tool(tc.getId(), result, isToolErrorResult(result)));
                }
                steps++;
                if (isTerminalSuccess(options)) {
                    return buildTerminalToolSuccess(thinkingAccumulator, steps);
                }
                continue;
            }
            String content = response.getContent();
            if (content != null && !content.isBlank()) {
                return AgentRunResult.builder()
                        .content(content.trim())
                        .thinkingContent(toThinkingContent(thinkingAccumulator))
                        .stepsUsed(steps)
                        .build();
            }
            steps++;
            if (isTerminalSuccess(options)) {
                return buildTerminalToolSuccess(thinkingAccumulator, steps);
            }
        }
        if (isTerminalSuccess(options)) {
            return buildTerminalToolSuccess(thinkingAccumulator, steps);
        }
        return AgentRunResult.builder()
                .error("Agent 已达最大步数上限（" + maxSteps + "），请缩小查询范围或简化需求")
                .stepsUsed(steps)
                .build();
    }

    /**
     * 组装单次 LLM 请求。
     * Anthropic 协议下按全局配置决定是否启用 Prompt Caching 与 Extended Thinking。
     */
    private LlmChatRequest buildChatRequest(AgentRunOptions options, List<LlmMessage> messages) {
        LlmModelConfig modelConfig = options.getModelConfig();
        boolean effective = ThinkingPolicy.resolveEffective(modelConfig, options.getSessionThinkingEnabled());
        boolean anthropic = LlmProviderTypes.isAnthropic(modelConfig.getProvider());
        boolean openAi = LlmProviderTypes.isOpenAiCompatible(modelConfig.getProvider());
        return LlmChatRequest.builder()
                .messages(messages)
                .tools(options.getTools())
                .toolChoice(options.getToolChoice())
                .promptCaching(anthropic && aiLlmConfigService.isAgentPromptCaching())
                .extendedThinking(effective && anthropic)
                .reasoningEnabled(effective && openAi)
                .thinkingBudgetTokens(ThinkingPolicy.resolveBudget(modelConfig, aiLlmConfigService))
                .build();
    }

    /**
     * 判断工具返回 JSON 是否表示执行失败。
     * 约定：JSON 对象含 {@code error} 字段视为失败，Anthropic 侧会标记 {@code is_error=true}。
     */
    private LlmChatResponse chatStreamCollect(LlmModelConfig modelConfig, LlmChatRequest request,
                                            AgentRunListener listener, StringBuilder thinkingAccumulator) {
        final LlmChatResponse[] holder = new LlmChatResponse[1];
        llmProvider.chatStream(modelConfig, request, new LlmStreamCallback() {
            @Override
            public void onTextDelta(String delta) {
                listener.onTextDelta(delta);
            }

            @Override
            public void onThinkingDelta(String delta) {
                if (delta != null && !delta.isEmpty()) {
                    thinkingAccumulator.append(delta);
                    listener.onThinkingDelta(delta);
                }
            }

            @Override
            public void onComplete(LlmChatResponse response) {
                holder[0] = response;
            }
        });
        return holder[0];
    }

    private static void appendThinking(StringBuilder accumulator, String thinking) {
        if (thinking == null || thinking.isBlank()) {
            return;
        }
        if (accumulator.length() > 0) {
            accumulator.append('\n');
        }
        accumulator.append(thinking.trim());
    }

    private static String toThinkingContent(StringBuilder accumulator) {
        if (accumulator.length() == 0) {
            return null;
        }
        return accumulator.toString().trim();
    }

    static boolean isToolErrorResult(String result) {
        return FlowDesignToolSupport.isErrorResult(result);
    }

    private static boolean isTerminalSuccess(AgentRunOptions options) {
        BooleanSupplier probe = options.getTerminalSuccessProbe();
        return probe != null && probe.getAsBoolean();
    }

    private static AgentRunResult buildTerminalToolSuccess(StringBuilder thinkingAccumulator, int stepsUsed) {
        return AgentRunResult.builder()
                .terminalViaTool(true)
                .thinkingContent(toThinkingContent(thinkingAccumulator))
                .stepsUsed(stepsUsed)
                .build();
    }

    /** Agent 单次运行入参 */
    @Getter
    @Builder
    public static class AgentRunOptions {
        /** 已 resolve 的模型运行时配置 */
        private final LlmModelConfig modelConfig;
        /** 首轮消息（通常含 system + user） */
        private final List<LlmMessage> initialMessages;
        /** 可用工具定义 */
        private final List<Map<String, Object>> tools;
        /** 工具调用策略，如 auto */
        private final String toolChoice;
        /** 宿主实现的工具执行回调，返回 tool 消息 content（JSON 字符串） */
        private final ToolExecutor toolExecutor;
        /** 最大 tool 轮数；≤0 时读 {@link AiLlmConfigService#getMaxSteps()} */
        private final int maxSteps;
        /** 可选流式事件回调（SSE 场景） */
        private final AgentRunListener listener;
        /** 会话思考开关：0 关、1 开、null 跟随模型默认 */
        private final Integer sessionThinkingEnabled;
        /**
         * 可选：探测业务是否已通过工具达成终态（如无正文但 submit 已成功）。
         * 返回 true 时视为成功，{@link AgentRunResult#isOk()} 为 true。
         */
        private final BooleanSupplier terminalSuccessProbe;
    }

    /** 工具执行回调，由业务场景实现具体逻辑 */
    public interface ToolExecutor {
        String execute(String toolName, String argumentsJson);
    }

    /** Agent 运行结果 */
    @Getter
    @Builder
    public static class AgentRunResult {
        /** 模型最终文本输出 */
        private final String content;
        /** Extended Thinking 等思考过程全文 */
        private final String thinkingContent;
        /** 失败时的可读错误信息 */
        private final String error;
        /** 实际消耗的 tool 轮数 */
        private final int stepsUsed;
        /** 无自然语言正文，但宿主判定工具已达成终态（如 submit patch） */
        private final boolean terminalViaTool;

        public boolean isOk() {
            return error == null && (content != null || terminalViaTool);
        }
    }
}
