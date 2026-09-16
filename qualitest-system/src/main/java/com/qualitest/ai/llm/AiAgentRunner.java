package com.qualitest.ai.llm;

import com.alibaba.fastjson2.JSONObject;
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
 * 反复调用模型，处理工具调用闭环，直到返回最终文本、达到步数上限或被取消：
 * <ul>
 *   <li>返回 toolCalls → 执行工具 → 追加 tool 消息 → 继续</li>
 *   <li>返回非空文本 → 视为最终答案并结束</li>
 *   <li>超出 maxSteps → 返回可读错误</li>
 *   <li>designSubmitNudgeEnabled 且剩余步数 ≤2 → 注入催促继续改图或收尾总结的提示</li>
 *   <li>cancelled 为 true → 在步间停止，返回 interrupted 及已记录的工具轨迹</li>
 * </ul>
 * 造流默认不启用终态探针：单次 submit 成功不立刻结束循环，由模型收尾总结；
 * 累积的画布改动由编排层从 SubmitCapture 读取。
 * 正在进行的单次模型 HTTP 请求不会被中途打断，等本次响应返回后再检查取消。
 */
@Component
@RequiredArgsConstructor
public class AiAgentRunner {

    /** 造流步数将尽时追加的 user 提示：催促 submit_* 或停止拉详情并总结 */
    static final String SUBMIT_NUDGE_CONTENT =
            "剩余工具步数不足（≤2）。若本轮要改画布，请继续调用对应的 submit_* 单元工具；"
                    + "若已完成请停止调工具并给出中文总结。禁止再反复拉取接口详情。";

    /** 用户取消或连接中断后，写入助手消息时的默认说明文案 */
    public static final String INTERRUPTED_MESSAGE = "本轮已中断";

    private final LlmProvider llmProvider;
    private final AiLlmConfigService aiLlmConfigService;

    /**
     * 执行 Agent 主循环。
     *
     * @param options 模型、消息、工具、执行器、步数上限、可选取消标志与监听器
     * @return 成功时 content 为最终正文；失败时 error 有说明；取消时 interrupted 为 true 并带已有轨迹
     */
    public AgentRunResult run(AgentRunOptions options) {
        List<LlmMessage> messages = new ArrayList<>(options.getInitialMessages());
        int maxSteps = options.getMaxSteps() > 0 ? options.getMaxSteps() : aiLlmConfigService.getMaxSteps();
        AgentRunListener listener = options.getListener();
        StringBuilder thinkingAccumulator = new StringBuilder();
        AiToolTraceSupport.Recorder toolTrace = new AiToolTraceSupport.Recorder();
        int steps = 0;
        while (steps < maxSteps) {
            if (isCancelled(options)) {
                return buildInterrupted(thinkingAccumulator, steps, maxSteps, toolTrace, null);
            }
            LlmChatRequest request = buildChatRequest(options, messages);
            LlmChatResponse response = listener != null
                    ? chatStreamCollect(options.getModelConfig(), request, listener, thinkingAccumulator)
                    : llmProvider.chat(options.getModelConfig(), request);
            if (response == null) {
                if (isCancelled(options)) {
                    return buildInterrupted(thinkingAccumulator, steps, maxSteps, toolTrace, null);
                }
                return AgentRunResult.builder()
                        .error("LLM 流式响应未完成")
                        .stepsUsed(steps)
                        .toolTrace(toolTrace.build(steps, maxSteps))
                        .build();
            }
            appendThinking(thinkingAccumulator, response.getThinkingContent());
            if (isCancelled(options)) {
                String partial = response.getContent() != null && !response.getContent().isBlank()
                        ? response.getContent().trim()
                        : null;
                return buildInterrupted(thinkingAccumulator, steps, maxSteps, toolTrace, partial);
            }
            if (response.getToolCalls() != null && !response.getToolCalls().isEmpty()) {
                messages.add(LlmMessage.assistant(response.getContent(), response.getToolCalls()));
                for (LlmToolCall tc : response.getToolCalls()) {
                    if (isCancelled(options)) {
                        return buildInterrupted(thinkingAccumulator, steps, maxSteps, toolTrace, null);
                    }
                    if (listener != null) {
                        listener.onToolStart(tc.getName());
                    }
                    long started = System.nanoTime();
                    String result = options.getToolExecutor().execute(tc.getName(), tc.getArgumentsJson());
                    long ms = (System.nanoTime() - started) / 1_000_000L;
                    toolTrace.record(tc.getName(), tc.getArgumentsJson(), result, ms, maxSteps);
                    if (listener != null) {
                        listener.onToolEnd(tc.getName());
                    }
                    messages.add(LlmMessage.tool(tc.getId(), result, isToolErrorResult(result)));
                }
                steps++;
                if (isCancelled(options)) {
                    return buildInterrupted(thinkingAccumulator, steps, maxSteps, toolTrace, null);
                }
                if (isTerminalSuccess(options)) {
                    return buildTerminalToolSuccess(thinkingAccumulator, steps, toolTrace.build(steps, maxSteps));
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
                        .toolTrace(toolTrace.build(steps, maxSteps))
                        .build();
            }
            steps++;
            if (isTerminalSuccess(options)) {
                return buildTerminalToolSuccess(thinkingAccumulator, steps, toolTrace.build(steps, maxSteps));
            }
            maybeAppendSubmitNudge(messages, options, steps, maxSteps);
        }
        if (isTerminalSuccess(options)) {
            return buildTerminalToolSuccess(thinkingAccumulator, steps, toolTrace.build(steps, maxSteps));
        }
        return AgentRunResult.builder()
                .error(maxStepsExceededMessage(maxSteps, options))
                .stepsUsed(steps)
                .toolTrace(toolTrace.build(steps, maxSteps))
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

    /** 当前请求是否已标记取消 */
    static boolean isCancelled(AgentRunOptions options) {
        BooleanSupplier cancelled = options.getCancelled();
        return cancelled != null && cancelled.getAsBoolean();
    }

    /**
     * 中断或失败落盘时选用的助手摘要文案。
     * 优先用已有正文；否则用非空且非默认中断文案的 error；再否则用「本轮已中断」。
     *
     * @param content 模型已产生的正文，可为 null
     * @param error   错误或中断说明，可为 null
     */
    public static String resolveInterruptedSummary(String content, String error) {
        if (content != null && !content.isBlank()) {
            return content.trim();
        }
        if (error != null && !error.isBlank() && !INTERRUPTED_MESSAGE.equals(error)) {
            return error.trim();
        }
        return INTERRUPTED_MESSAGE;
    }

    /**
     * 构造因取消而结束的运行结果：带已有思考、轨迹与可选部分正文。
     */
    private static AgentRunResult buildInterrupted(
            StringBuilder thinkingAccumulator,
            int stepsUsed,
            int maxSteps,
            AiToolTraceSupport.Recorder toolTrace,
            String partialContent) {
        return AgentRunResult.builder()
                .interrupted(true)
                .content(partialContent)
                .thinkingContent(toThinkingContent(thinkingAccumulator))
                .error(INTERRUPTED_MESSAGE)
                .stepsUsed(stepsUsed)
                .toolTrace(toolTrace.build(stepsUsed, maxSteps))
                .build();
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
    private static AgentRunResult buildTerminalToolSuccess(
            StringBuilder thinkingAccumulator, int stepsUsed, JSONObject toolTrace) {
        return AgentRunResult.builder()
                .terminalViaTool(true)
                .thinkingContent(toThinkingContent(thinkingAccumulator))
                .stepsUsed(stepsUsed)
                .toolTrace(toolTrace)
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
        /** 最大工具轮数；≤0 时使用全局配置中的上限 */
        private final int maxSteps;
        /** 流式过程事件回调，可为 null */
        private final AgentRunListener listener;
        /** 会话思考开关：0 关、1 开、null 跟随模型默认 */
        private final Integer sessionThinkingEnabled;
        /**
         * 可选终态探测：返回 true 时立刻成功结束循环（无需等模型再写正文）。
         */
        private final BooleanSupplier terminalSuccessProbe;
        /**
         * 为 true 时在步数将尽时向对话追加催促改图或收尾的提示。
         */
        @Builder.Default
        private final boolean designSubmitNudgeEnabled = false;
        /**
         * 取消标志：用户取消或 SSE 断连后为 true。
         * 循环在每轮模型返回后、每组工具执行前后检查；为 true 则停止并返回 interrupted。
         */
        private final BooleanSupplier cancelled;
    }

    /** 宿主提供的工具执行实现，返回写入 tool 消息的 content（一般为 JSON 字符串） */
    public interface ToolExecutor {
        String execute(String toolName, String argumentsJson);
    }

    /** Agent 单次运行结果 */
    @Getter
    @Builder
    public static class AgentRunResult {
        /** 模型最终正文；中断时可能仅为部分文本 */
        private final String content;
        /** 思考过程全文，仅展示用 */
        private final String thinkingContent;
        /** 失败或中断时的说明文案 */
        private final String error;
        /** 实际消耗的工具轮数 */
        private final int stepsUsed;
        /** 无正文但业务侧判定工具已达成终态 */
        private final boolean terminalViaTool;
        /**
         * 本轮工具调用轨迹（已脱敏截断）。
         * 字段含 stepsUsed、maxSteps、truncated、calls（每项含 i、name、ok、ms、args、result）。
         */
        private final JSONObject toolTrace;
        /** 因用户取消或连接中断而停止 */
        private final boolean interrupted;

        /** 未中断、无 error，且有正文或终态探针成功 */
        public boolean isOk() {
            return !interrupted && error == null && (content != null || terminalViaTool);
        }
    }
}
