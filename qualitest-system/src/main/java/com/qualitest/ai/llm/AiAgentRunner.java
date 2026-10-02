package com.qualitest.ai.llm;

import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.config.AiLlmConfigService;
import com.qualitest.ai.llm.lc4j.Lc4jClientFactory;
import com.qualitest.ai.llm.lc4j.Lc4jToolSpecifications;
import com.qualitest.ai.tools.FlowDesignToolSupport;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ToolChoice;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.PartialThinking;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;

/**
 * 通用 Agent 工具循环执行器。
 * <p>
 * 按步调用大模型：有监听器时走流式（正文/思考增量推前端），否则走同步一次返回。
 * 每步若返回工具调用则执行宿主回调、把结果写回对话继续下一轮；
 * 若返回纯文本则结束；也可因步数用尽、用户取消、或宿主终态探测成功而结束。
 */
@Component
@RequiredArgsConstructor
public class AiAgentRunner {

    /** 造流场景：剩余步数将尽时追加的用户提示，催促提交改图工具或停止拉详情并总结。 */
    static final String SUBMIT_NUDGE_CONTENT =
            "剩余工具步数不足（≤2）。若本轮要改画布，请继续调用对应的 submit_* 单元工具；"
                    + "若已完成请停止调工具并给出中文总结。禁止再反复拉取接口详情。";

    /** 用户取消或连接中断后，写入助手消息时的默认说明文案。 */
    public static final String INTERRUPTED_MESSAGE = "本轮已中断";

    /**
     * 输出语言提醒：追加在本轮用户消息末尾，要求模型调用工具前的说明与最终回复都用简体中文，
     * 工具前的说明至多一句。只发给模型，不保存到会话记录。
     */
    public static final String OUTPUT_LANGUAGE_REMINDER =
            "【输出语言】调用工具前的说明与最终回复一律使用简体中文；工具前至多一句，如「先查看画布和可用子流。」。";

    private final Lc4jClientFactory lc4jClientFactory;
    private final AiLlmConfigService aiLlmConfigService;

    /**
     * 执行 Agent 主循环。
     * 业务异常原样抛出；其它运行时异常转为带中文说明的业务异常（不透出上游原始 JSON）。
     *
     * @param options 模型配置、首轮消息、工具定义、工具执行器、步数上限、取消标志、监听器等
     * @return 成功时 content 为最终正文；失败时 error 有说明；取消时 interrupted 为 true 并带已有工具轨迹
     */
    public AgentRunResult run(AgentRunOptions options) {
        try {
            return runInternal(options);
        } catch (LlmClientException e) {
            throw e;
        } catch (RuntimeException e) {
            // 上游/网络等运行时异常 → 用户可读中文
            throw new LlmClientException(LlmUpstreamErrorMessages.forChat(e), e);
        }
    }

    private AgentRunResult runInternal(AgentRunOptions options) {
        List<ChatMessage> messages = new ArrayList<>(options.getInitialMessages());
        int maxSteps = options.getMaxSteps() > 0 ? options.getMaxSteps() : aiLlmConfigService.getMaxSteps();
        AgentRunListener listener = options.getListener();
        RunProgress progress = new RunProgress();
        boolean reasoningEnabled = ThinkingPolicy.resolveEffective(
                options.getModelConfig(), options.getSessionThinkingEnabled());
        List<ToolSpecification> toolSpecs = Lc4jToolSpecifications.fromOpenAiMaps(options.getTools());
        StreamingChatModel streamingModel = listener != null
                ? lc4jClientFactory.streamingChatModel(options.getModelConfig(), reasoningEnabled)
                : null;
        ChatModel chatModel = listener == null
                ? lc4jClientFactory.chatModel(options.getModelConfig(), reasoningEnabled)
                : null;

        int steps = 0;
        while (steps < maxSteps) {
            if (isCancelled(options)) {
                return buildInterrupted(progress, steps, maxSteps, null);
            }
            ChatRequest request = buildChatRequest(messages, toolSpecs);
            ChatResponse response = listener != null
                    ? chatStreamCollect(streamingModel, request, listener, reasoningEnabled)
                    : chatModel.chat(request);
            if (response == null || response.aiMessage() == null) {
                if (isCancelled(options)) {
                    return buildInterrupted(progress, steps, maxSteps, null);
                }
                return progress.resultBuilder(steps, maxSteps)
                        .error("LLM 流式响应未完成")
                        .build();
            }
            AiMessage aiMessage = response.aiMessage();
            // 本轮思考追加到思考全文，记下字符数，用于把思考全文按步切开
            int thinkingChars = progress.appendThinking(aiMessage.thinking());
            if (isCancelled(options)) {
                String partial = aiMessage.text() != null && !aiMessage.text().isBlank()
                        ? aiMessage.text().trim()
                        : null;
                return buildInterrupted(progress, steps, maxSteps, partial);
            }
            // 带工具调用的一步：记录本步说明文字，依次执行工具，结果写回对话后进入下一轮
            if (aiMessage.hasToolExecutionRequests()) {
                JSONObject stepRecord = progress.startStep(steps + 1, aiMessage.text(), thinkingChars);
                messages.add(toHistoryAiMessage(aiMessage, reasoningEnabled));
                for (ToolExecutionRequest tc : aiMessage.toolExecutionRequests()) {
                    if (isCancelled(options)) {
                        return buildInterrupted(progress, steps, maxSteps, null);
                    }
                    String toolName = tc.name();
                    String argsJson = tc.arguments() != null ? tc.arguments() : "{}";
                    if (listener != null) {
                        listener.onToolStart(toolName);
                    }
                    long started = System.nanoTime();
                    String result = options.getToolExecutor().execute(toolName, argsJson);
                    long ms = (System.nanoTime() - started) / 1_000_000L;
                    // 写入工具轨迹，并把本次调用序号记到所属步骤
                    progress.toolTrace.record(toolName, argsJson, result, ms, maxSteps);
                    progress.markCallDone(stepRecord);
                    if (listener != null) {
                        listener.onToolEnd(toolName);
                    }
                    boolean toolError = isToolErrorResult(result);
                    messages.add(ToolExecutionResultMessage.builder()
                            .id(tc.id())
                            .toolName(toolName)
                            .text(result != null ? result : "")
                            .isError(toolError)
                            .build());
                }
                steps++;
                if (isCancelled(options)) {
                    return buildInterrupted(progress, steps, maxSteps, null);
                }
                if (isTerminalSuccess(options)) {
                    return buildTerminalToolSuccess(progress, steps, maxSteps);
                }
                maybeAppendSubmitNudge(messages, options, steps, maxSteps);
                continue;
            }
            // 无工具调用且有正文：作为最终回复，结束本轮
            String content = aiMessage.text();
            if (content != null && !content.isBlank()) {
                return progress.resultBuilder(steps, maxSteps)
                        .content(content.trim())
                        .build();
            }
            // 既无工具调用也无正文的空步：仍记一条步骤，保证按 thinkingChars 切分思考全文时不错位
            progress.startStep(steps + 1, null, thinkingChars);
            steps++;
            if (isTerminalSuccess(options)) {
                return buildTerminalToolSuccess(progress, steps, maxSteps);
            }
            maybeAppendSubmitNudge(messages, options, steps, maxSteps);
        }
        if (isTerminalSuccess(options)) {
            return buildTerminalToolSuccess(progress, steps, maxSteps);
        }
        return progress.resultBuilder(steps, maxSteps)
                .error(maxStepsExceededMessage(maxSteps, options))
                .build();
    }

    /**
     * 单次运行中的累积状态：思考全文、按步时间线、工具轨迹、工具调用计数。
     * <p>
     * 按步时间线每条字段：
     * <ul>
     *   <li>step：第几轮模型调用，从 1 开始</li>
     *   <li>narration：该步调用工具前模型输出的说明文字，无则不写</li>
     *   <li>callFrom / callTo：该步执行的工具调用在本轮所有调用中的起止序号（从 1 开始），未执行调用则不写</li>
     *   <li>thinkingChars：该步追加进思考全文的字符数，按顺序累加即可从思考全文切出每步的思考</li>
     * </ul>
     * 最终纯文本回复那一步不记入时间线，它的思考是思考全文切完各步后剩下的部分。
     */
    private static final class RunProgress {
        /** 本轮思考全文，各步之间以一个换行分隔 */
        private final StringBuilder thinking = new StringBuilder();
        /** 按步时间线 */
        private final JSONArray agentSteps = new JSONArray();
        /** 工具调用轨迹 */
        private final AiToolTraceSupport.Recorder toolTrace = new AiToolTraceSupport.Recorder();
        /** 已执行的工具调用总数（含因轨迹条数上限未写入轨迹的调用） */
        private int callSeq;

        /**
         * 将本轮完整思考文本追加到思考全文（多轮之间用换行分隔）。
         *
         * @return 本次追加的思考字符数（不含分隔换行）；无内容时为 0
         */
        private int appendThinking(String text) {
            if (text == null || text.isBlank()) {
                return 0;
            }
            if (thinking.length() > 0) {
                thinking.append('\n');
            }
            String trimmed = text.trim();
            thinking.append(trimmed);
            return trimmed.length();
        }

        /**
         * 追加一条步骤记录并返回，执行工具时再往里回填 callFrom / callTo。
         *
         * @param step          第几轮模型调用
         * @param text          该步模型输出的说明文字，可为空
         * @param thinkingChars 该步思考字符数
         */
        private JSONObject startStep(int step, String text, int thinkingChars) {
            JSONObject record = new JSONObject();
            record.put("step", step);
            if (text != null && !text.isBlank()) {
                record.put("narration", text.trim());
            }
            record.put("thinkingChars", thinkingChars);
            agentSteps.add(record);
            return record;
        }

        /** 一次工具调用执行完毕：调用计数加一，并更新所属步骤的起止序号。 */
        private void markCallDone(JSONObject stepRecord) {
            callSeq++;
            if (!stepRecord.containsKey("callFrom")) {
                stepRecord.put("callFrom", callSeq);
            }
            stepRecord.put("callTo", callSeq);
        }

        /** 把各步说明文字按顺序用换行拼成过程旁白全文；一句都没有时返回 null。 */
        private String joinNarration() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < agentSteps.size(); i++) {
                String text = agentSteps.getJSONObject(i).getString("narration");
                if (text == null) {
                    continue;
                }
                if (sb.length() > 0) {
                    sb.append('\n');
                }
                sb.append(text);
            }
            return sb.length() == 0 ? null : sb.toString();
        }

        /** 生成已填好思考全文、过程旁白、按步时间线、已用步数与工具轨迹的结果构建器，各出口在此基础上补充自身字段。 */
        private AgentRunResult.AgentRunResultBuilder resultBuilder(int stepsUsed, int maxSteps) {
            return AgentRunResult.builder()
                    .thinkingContent(thinking.length() == 0 ? null : thinking.toString())
                    .processNarration(joinNarration())
                    .agentSteps(agentSteps.isEmpty() ? null : agentSteps)
                    .stepsUsed(stepsUsed)
                    .toolTrace(toolTrace.build(stepsUsed, maxSteps));
        }
    }

    /**
     * 组装单轮模型请求：消息列表 + 可选工具规格。
     * 当前工具选择策略固定为 AUTO（由模型决定是否调工具）。
     */
    private static ChatRequest buildChatRequest(List<ChatMessage> messages,
                                                List<ToolSpecification> tools) {
        ChatRequest.Builder builder = ChatRequest.builder().messages(messages);
        if (tools != null && !tools.isEmpty()) {
            builder.toolSpecifications(tools);
            builder.toolChoice(ToolChoice.AUTO);
        }
        return builder.build();
    }

    /**
     * 发起流式调用并阻塞至完整响应。
     * 正文增量、思考增量实时回调监听器；完整思考文本在结束后由主循环累加，避免与增量重复写入。
     * 超时时间为读超时配置 + 30 秒缓冲。
     */
    private ChatResponse chatStreamCollect(StreamingChatModel model,
                                           ChatRequest request,
                                           AgentRunListener listener,
                                           boolean reasoningEnabled) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<ChatResponse> holder = new AtomicReference<>();
        AtomicReference<Throwable> errorHolder = new AtomicReference<>();
        model.chat(request, new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String partialResponse) {
                if (partialResponse != null && !partialResponse.isEmpty()) {
                    listener.onTextDelta(partialResponse);
                }
            }

            @Override
            public void onPartialThinking(PartialThinking partialThinking) {
                if (!reasoningEnabled || partialThinking == null) {
                    return;
                }
                String delta = partialThinking.text();
                if (delta != null && !delta.isEmpty()) {
                    // 仅推前端展示；完整思考在本轮结束后写入累加器，避免重复拼接
                    listener.onThinkingDelta(delta);
                }
            }

            @Override
            public void onCompleteResponse(ChatResponse completeResponse) {
                holder.set(completeResponse);
                latch.countDown();
            }

            @Override
            public void onError(Throwable error) {
                errorHolder.set(error);
                latch.countDown();
            }
        });
        try {
            long timeoutMs = Math.max(aiLlmConfigService.getReadTimeoutMs(), 1000L) + 30_000L;
            if (!latch.await(timeoutMs, TimeUnit.MILLISECONDS)) {
                throw new LlmClientException("LLM 流式响应超时");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmClientException("LLM 流式响应被中断", e);
        }
        if (errorHolder.get() != null) {
            Throwable err = errorHolder.get();
            if (err instanceof RuntimeException re) {
                throw re;
            }
            throw new LlmClientException("LLM 流式调用失败: " + err.getMessage(), err);
        }
        return holder.get();
    }

    /**
     * 把本轮助手消息写入多轮上下文。
     * 开启思考时，即使上游未返回 thinking 也写入空串，避免部分厂商在后续工具轮丢失思考字段。
     */
    private static AiMessage toHistoryAiMessage(AiMessage aiMessage, boolean reasoningEnabled) {
        String thinking = aiMessage.thinking();
        if (reasoningEnabled && thinking == null) {
            thinking = "";
        }
        return AiMessage.builder()
                .text(aiMessage.text())
                .thinking(thinking)
                .toolExecutionRequests(aiMessage.toolExecutionRequests())
                .attributes(aiMessage.attributes())
                .build();
    }

    /**
     * 判断工具返回是否为错误结果。
     * 约定：返回 JSON 顶层含 error 字段时视为失败，写入 tool 消息时打上错误标记。
     */
    static boolean isToolErrorResult(String result) {
        return FlowDesignToolSupport.isErrorResult(result);
    }

    /** 当前请求是否已标记取消（用户取消或 SSE 断连）。 */
    static boolean isCancelled(AgentRunOptions options) {
        BooleanSupplier cancelled = options.getCancelled();
        return cancelled != null && cancelled.getAsBoolean();
    }

    /**
     * 中断或失败落盘时选用的助手摘要文案。
     * 优先已有正文；其次用非空且非默认中断文案的 error；最后用「本轮已中断」。
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

    /** 组装取消/中断结果：带已有思考、过程旁白、按步时间线、可选半成品正文与工具轨迹。 */
    private static AgentRunResult buildInterrupted(
            RunProgress progress, int stepsUsed, int maxSteps, String partialContent) {
        return progress.resultBuilder(stepsUsed, maxSteps)
                .interrupted(true)
                .content(partialContent)
                .error(INTERRUPTED_MESSAGE)
                .build();
    }

    /** 宿主终态探测是否已成功（例如造流已提交改图）。 */
    private static boolean isTerminalSuccess(AgentRunOptions options) {
        BooleanSupplier probe = options.getTerminalSuccessProbe();
        return probe != null && probe.getAsBoolean();
    }

    /**
     * 造流：剩余步数 ≤2 且尚未终态成功时，向对话末尾追加催促提示。
     * 若末条已是同一催促文案则不重复追加。
     */
    static void maybeAppendSubmitNudge(
            List<ChatMessage> messages, AgentRunOptions options, int steps, int maxSteps) {
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
            ChatMessage last = messages.get(messages.size() - 1);
            if (last instanceof UserMessage user
                    && user.hasSingleText()
                    && SUBMIT_NUDGE_CONTENT.equals(user.singleText())) {
                return;
            }
        }
        messages.add(UserMessage.from(SUBMIT_NUDGE_CONTENT));
    }

    /** 步数耗尽时的错误说明；造流场景文案更侧重催促提交改图。 */
    static String maxStepsExceededMessage(int maxSteps, AgentRunOptions options) {
        if (options.isDesignSubmitNudgeEnabled() || options.getTerminalSuccessProbe() != null) {
            return "Agent 已达最大步数上限（" + maxSteps + "）。"
                    + "若本轮要改画布，请缩小检索范围并尽快调用对应 submit_* 单元工具；"
                    + "或结束本轮后重新发送更短的改图需求。";
        }
        return "Agent 已达最大步数上限（" + maxSteps + "），请缩小查询范围或简化需求";
    }

    /** 因宿主终态探测成功而结束（无最终正文，依赖工具侧已落结果）。 */
    private static AgentRunResult buildTerminalToolSuccess(RunProgress progress, int stepsUsed, int maxSteps) {
        return progress.resultBuilder(stepsUsed, maxSteps)
                .terminalViaTool(true)
                .build();
    }

    /** Agent 单次运行入参。 */
    @Getter
    @Builder
    public static class AgentRunOptions {
        /** 已解析的模型运行时配置（连接信息、模型名、超时等）。 */
        private final LlmModelConfig modelConfig;
        /** 首轮送入模型的消息（通常含 system、摘要、历史、本轮 user）。 */
        private final List<ChatMessage> initialMessages;
        /** 可用工具定义（OpenAI 风格 Map：function.name / description / parameters）。 */
        private final List<Map<String, Object>> tools;
        /** 工具选择策略字符串（预留字段；当前实现固定 AUTO）。 */
        private final String toolChoice;
        /** 宿主工具执行回调，入参为工具名与 arguments JSON，返回写入 tool 消息的 content。 */
        private final ToolExecutor toolExecutor;
        /** 最大工具轮数；≤0 时使用全局配置上限。 */
        private final int maxSteps;
        /** 流式过程事件回调；非空时走流式客户端，可为 null 走同步。 */
        private final AgentRunListener listener;
        /** 会话思考开关：0 关、1 开、null 跟随模型默认能力。 */
        private final Integer sessionThinkingEnabled;
        /** 可选终态探测：返回 true 时立刻按工具成功结束循环。 */
        private final BooleanSupplier terminalSuccessProbe;
        /** 为 true 时在步数将尽时向对话追加催促改图或收尾的提示。 */
        @Builder.Default
        private final boolean designSubmitNudgeEnabled = false;
        /** 取消标志：用户取消或 SSE 断连后为 true。 */
        private final BooleanSupplier cancelled;
    }

    /** 宿主提供的工具执行实现。 */
    public interface ToolExecutor {
        /**
         * 执行一次工具调用。
         *
         * @param toolName      工具名
         * @param argumentsJson 模型给出的参数 JSON 字符串
         * @return 写入 tool 消息的 content（一般为 JSON 字符串）
         */
        String execute(String toolName, String argumentsJson);
    }

    /** Agent 单次运行结果。 */
    @Getter
    @Builder
    public static class AgentRunResult {
        /** 最终助手正文；终态经工具成功结束时可为 null。 */
        private final String content;
        /** 本轮累加的思考链全文；未开启或无内容时为 null。 */
        private final String thinkingContent;
        /**
         * 过程旁白：各步调用工具前模型输出的说明文字，按步用换行拼接，不含最终回复；无则 null。
         * 前端在本轮结束后把它显示在总结前面。
         */
        private final String processNarration;
        /**
         * 按步时间线（不含最终纯文本回复那一步）；无步骤时为 null。
         * 每条含 step、narration、callFrom / callTo、thinkingChars，可还原说明文字、工具调用与思考的先后顺序。
         */
        private final JSONArray agentSteps;
        /** 失败或中断说明；成功时为 null。 */
        private final String error;
        /** 已消耗的工具轮数。 */
        private final int stepsUsed;
        /** true 表示因宿主终态探测成功而结束（例如已提交改图）。 */
        private final boolean terminalViaTool;
        /** 工具调用轨迹（供落库与前端展示）。 */
        private final JSONObject toolTrace;
        /** true 表示用户取消或连接中断。 */
        private final boolean interrupted;

        /** 未中断、无 error，且有正文或经工具终态成功。 */
        public boolean isOk() {
            return !interrupted && error == null && (content != null || terminalViaTool);
        }

        /**
         * 把本轮过程信息写入助手消息元数据，各字段无内容时不写：
         * <ul>
         *   <li>toolTrace：工具调用轨迹</li>
         *   <li>processNarration：过程旁白</li>
         *   <li>agentSteps：按步时间线</li>
         *   <li>agentReply：模型最后一轮纯文本回复原文，仅在它不同于 summary 时写入</li>
         * </ul>
         *
         * @param meta    助手消息元数据
         * @param summary 本轮最终写入气泡的 summary
         */
        public void writeProcessMeta(JSONObject meta, String summary) {
            if (toolTrace != null) {
                meta.put("toolTrace", toolTrace);
            }
            if (processNarration != null && !processNarration.isBlank()) {
                meta.put("processNarration", processNarration);
            }
            if (agentSteps != null && !agentSteps.isEmpty()) {
                meta.put("agentSteps", agentSteps);
            }
            if (content != null && !content.isBlank() && !content.trim().equals(summary)) {
                meta.put("agentReply", content.trim());
            }
        }
    }
}
