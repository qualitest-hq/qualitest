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
 * 反复调模型 chat，处理 tool 调用闭环，直到返回最终文本或达到步数上限：
 * <ul>
 *   <li>返回 toolCalls → 执行工具 → 追加 tool 消息 → 继续</li>
 *   <li>返回非空文本 → 视为最终答案并结束</li>
 *   <li>超出 maxSteps → 返回可读错误（造流场景会提示尽快 submit_*）</li>
 *   <li>designSubmitNudgeEnabled 且剩余步数 ≤2 → 注入催促继续 submit 或收尾总结</li>
 * </ul>
 * 造流默认不启用终态探针：任意单次 submit 成功不立刻结束循环，由模型收尾总结；
 * 累积结果由 SubmitCapture 在编排层读取。
 */
@Component
@RequiredArgsConstructor
public class AiAgentRunner {

    /** 造流步数将尽时追加的 user 提示：催促 submit_* 或停止拉详情并总结 */
    static final String SUBMIT_NUDGE_CONTENT =
            "剩余工具步数不足（≤2）。若本轮要改画布，请继续调用对应的 submit_* 单元工具；"
                    + "若已完成请停止调工具并给出中文总结。禁止再反复拉取接口详情。";

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
                maybeAppendSubmitNudge(messages, options, steps, maxSteps);
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
            maybeAppendSubmitNudge(messages, options, steps, maxSteps);
        }
        if (isTerminalSuccess(options)) {
            return buildTerminalToolSuccess(thinkingAccumulator, steps);
        }
        return AgentRunResult.builder()
                .error(maxStepsExceededMessage(maxSteps, options))
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

    /** 工具结果是否为错误 JSON（顶层含 error），供消息标记 */
    static boolean isToolErrorResult(String result) {
        return FlowDesignToolSupport.isErrorResult(result);
    }

    /** 可选终态探针为 true 时提前成功结束（造流通常不设） */
    private static boolean isTerminalSuccess(AgentRunOptions options) {
        BooleanSupplier probe = options.getTerminalSuccessProbe();
        return probe != null && probe.getAsBoolean();
    }

    /**
     * 造流：剩余步数 ≤2 时追加催促提示（同内容不重复追加）。
     * 已终态成功则不再催。
     */
    static void maybeAppendSubmitNudge(
            List<LlmMessage> messages, AgentRunOptions options, int steps, int maxSteps) {
        if (!options.isDesignSubmitNudgeEnabled()) {
            return;
        }
        if (isTerminalSuccess(options)) {
            return;
        }
        if (steps >= maxSteps || maxSteps - steps > 2) {
            return;
        }
        if (!messages.isEmpty()) {
            LlmMessage last = messages.get(messages.size() - 1);
            if ("user".equals(last.getRole()) && SUBMIT_NUDGE_CONTENT.equals(last.getContent())) {
                return;
            }
        }
        messages.add(LlmMessage.user(SUBMIT_NUDGE_CONTENT));
    }

    /** 达步数上限时的错误文案；造流额外提示缩小范围并尽快 submit_*。 */
    static String maxStepsExceededMessage(int maxSteps, AgentRunOptions options) {
        if (options.isDesignSubmitNudgeEnabled() || options.getTerminalSuccessProbe() != null) {
            return "Agent 已达最大步数上限（" + maxSteps + "）。"
                    + "若本轮要改画布，请缩小检索范围并尽快调用对应 submit_* 单元工具；"
                    + "或结束本轮后重新发送更短的改图需求。";
        }
        return "Agent 已达最大步数上限（" + maxSteps + "），请缩小查询范围或简化需求";
    }

    /** 终态探针触发时的成功结果（可无自然语言正文）。 */
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
         * 可选：探测业务是否已通过工具达成终态。
         * 返回 true 时立刻成功结束循环。造流分类型单单元提交通常不设，改由模型收尾总结。
         */
        private final BooleanSupplier terminalSuccessProbe;
        /**
         * 造流开关：步数将尽时注入催促继续 submit_* 或收尾总结的提示。
         */
        @Builder.Default
        private final boolean designSubmitNudgeEnabled = false;
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
