package com.qualitest.ai.scenario.apidesign;

import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.config.AiLlmConfigService;
import com.qualitest.ai.domain.AiChatSession;
import com.qualitest.ai.llm.AgentRunListener;
import com.qualitest.ai.llm.AiAgentRunner;
import com.qualitest.ai.llm.LlmClientException;
import com.qualitest.ai.llm.LlmMessage;
import com.qualitest.ai.llm.LlmModelConfig;
import com.qualitest.ai.llm.history.HistoryWindowPolicy;
import com.qualitest.ai.llm.history.HistoryWindowPolicyResolver;
import com.qualitest.ai.llm.history.TokenEstimator;
import com.qualitest.ai.llm.lc4j.Lc4jMessageSupport;
import com.qualitest.ai.scenario.apidesign.model.ApiDesignPatch;
import com.qualitest.ai.scenario.apidesign.model.ApiDesignRequest;
import com.qualitest.ai.scenario.apidesign.model.ApiDesignResult;
import com.qualitest.ai.scenario.apidesign.model.ApiDesignValidationResult;
import com.qualitest.ai.service.AiChatConversationService;
import com.qualitest.ai.service.AiChatSessionSummaryService;
import com.qualitest.ai.service.IAiLlmModelService;
import com.qualitest.ai.tools.apidesign.ApiDesignSubmitCapture;
import com.qualitest.ai.tools.apidesign.ApiDesignToolContext;
import com.qualitest.ai.tools.apidesign.ApiDesignToolContextFactory;
import com.qualitest.ai.tools.apidesign.ApiDesignToolExecutor;
import com.qualitest.ai.tools.apidesign.ApiDesignToolsDefinitionService;
import com.qualitest.common.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;

/**
 * AI API 助手场景编排：加载会话、跑工具循环、产出 ApiDesignPatch 或纯说明。
 * <p>
 * 半自动：submit 只进 SubmitCapture，前端 Diff 勾选后才合并进工作台草稿，人手保存接口库。
 * 全自动：system 追加全自动规程；submit 回执提示前端会自动应用草稿；仍不自动写接口库、不自动调试发送。
 * 用户取消或断连时：步间停止，仍尽量写入助手半成品（正文、思考、工具轨迹、已提交 patch），并标 interrupted。
 */
@Service
@RequiredArgsConstructor
public class ApiDesignAgent {

    private final IAiLlmModelService aiLlmModelService;
    private final AiLlmConfigService aiLlmConfigService;
    private final AiAgentRunner aiAgentRunner;
    private final ApiDesignToolExecutor apiDesignToolExecutor;
    private final ApiDesignToolContextFactory apiDesignToolContextFactory;
    private final ApiDesignToolsDefinitionService apiDesignToolsDefinitionService;
    private final AiChatConversationService aiChatConversationService;
    private final HistoryWindowPolicyResolver historyWindowPolicyResolver;
    private final AiChatSessionSummaryService aiChatSessionSummaryService;

    /** 执行一轮 API 设计对话，不推送流式事件。 */
    public ApiDesignResult design(ApiDesignRequest request, Long userId) {
        return design(request, userId, null, null);
    }

    /**
     * 执行一轮 API 设计对话：加载会话与工具、调用大模型、持久化助手消息并返回 patch 或纯说明。
     *
     * @param listener 可选；非空时推送 token、思考链与工具调用事件
     */
    public ApiDesignResult design(ApiDesignRequest request, Long userId, AgentRunListener listener) {
        return design(request, userId, listener, null);
    }

    /**
     * 流式入口：可传入取消标志。
     *
     * @param cancelled 用户取消或 SSE 断连后为 true；步间停止并把已有结果写入助手消息
     */
    public ApiDesignResult design(ApiDesignRequest request, Long userId,
                                  AgentRunListener listener, BooleanSupplier cancelled) {
        validateRequest(request);
        if (userId == null) {
            throw new ServiceException("未登录");
        }
        LlmModelConfig modelConfig = aiLlmModelService.resolve(request.getAiLlmModelId());

        String bizRef = buildBizRefJson(request.getTestProjectApiId());
        AiChatSession session = aiChatConversationService.loadOrCreate(
                request.getAiChatSessionId(),
                AiChatConversationService.SCENE_TEST_API_DESIGN,
                request.getTestProjectId(),
                bizRef,
                userId,
                request.getAiLlmModelId(),
                request.getPrompt(),
                request.getAiChatSessionId() == null ? request.getThinkingEnabled() : null);

        if (listener != null && session.getAiChatSessionId() != null) {
            // 会话就绪后立刻回调，便于流式接口尽早推送 session 事件
            listener.onSessionReady(session.getAiChatSessionId());
        }

        Integer sessionThinking = resolveSessionThinking(session, request);

        ApiDesignSubmitCapture submitCapture = new ApiDesignSubmitCapture();
        ApiDesignToolContext toolContext = apiDesignToolContextFactory.fromDesignRequest(request, submitCapture);

        List<Map<String, Object>> tools = apiDesignToolsDefinitionService.loadToolsDefinition();
        boolean autopilot = request.isAutopilotEnabledEffective();
        List<ChatMessage> messages = buildInitialMessages(request, session, modelConfig, autopilot);

        aiChatConversationService.appendUserMessage(
                session.getAiChatSessionId(),
                request.getPrompt().trim(),
                null,
                request.getAiLlmModelId());

        AiAgentRunner.AgentRunResult runResult = aiAgentRunner.run(AiAgentRunner.AgentRunOptions.builder()
                .modelConfig(modelConfig)
                .initialMessages(messages)
                .tools(tools)
                .toolChoice("auto")
                .toolExecutor((name, args) -> apiDesignToolExecutor.executeTool(name, args, toolContext))
                .maxSteps(aiLlmConfigService.getMaxSteps())
                .listener(listener)
                .sessionThinkingEnabled(sessionThinking)
                .cancelled(cancelled)
                .build());

        boolean interrupted = runResult.isInterrupted();
        if (!runResult.isOk() && !interrupted) {
            persistApiAssistant(request, userId, session, modelConfig, runResult, submitCapture, true);
            throw new LlmClientException(runResult.getError());
        }

        return persistApiAssistant(request, userId, session, modelConfig, runResult, submitCapture, interrupted);
    }

    /**
     * 将本轮助手结果写入会话并组装返回体。
     * 中断或硬失败时写入 interrupted、已有正文或默认中断文案、工具轨迹与已提交的 patch。
     */
    private ApiDesignResult persistApiAssistant(
            ApiDesignRequest request,
            Long userId,
            AiChatSession session,
            LlmModelConfig modelConfig,
            AiAgentRunner.AgentRunResult runResult,
            ApiDesignSubmitCapture submitCapture,
            boolean interruptedOrFailed) {
        String content = runResult.getContent();
        boolean explainOnly = !submitCapture.isSubmitted();
        ApiDesignPatch normalizedPatch = explainOnly ? null : submitCapture.getNormalizedPatch();
        ApiDesignValidationResult validation;

        if (explainOnly) {
            validation = ApiDesignValidationResult.builder()
                    .ok(true)
                    .errors(List.of())
                    .warnings(List.of())
                    .build();
        } else {
            if (normalizedPatch == null && !interruptedOrFailed) {
                throw new LlmClientException("submit_api_design_patch 未产生有效 patch");
            }
            validation = submitCapture.getValidation();
            if (validation == null) {
                validation = ApiDesignValidationResult.builder()
                        .ok(true)
                        .errors(List.of())
                        .warnings(List.of())
                        .build();
            }
        }

        String summary;
        if (interruptedOrFailed) {
            summary = AiAgentRunner.resolveInterruptedSummary(content, runResult.getError());
        } else {
            summary = resolveSummary(normalizedPatch, content, explainOnly);
        }

        JSONObject meta = new JSONObject();
        meta.put("summary", summary);
        meta.put("explainOnly", explainOnly);
        meta.put("vendorName", modelConfig.getVendorName());
        meta.put("modelName", modelConfig.getModelName());
        if (interruptedOrFailed) {
            meta.put("interrupted", true);
        }
        if (!explainOnly && normalizedPatch != null) {
            meta.put("patchJson", normalizedPatch);
        }
        // 脱敏截断后的工具轨迹，供气泡折叠展开排障
        if (runResult.getToolTrace() != null) {
            meta.put("toolTrace", runResult.getToolTrace());
        }

        aiChatConversationService.appendAssistantMessage(
                session.getAiChatSessionId(),
                summary,
                meta.toJSONString(),
                request.getAiLlmModelId(),
                runResult.getThinkingContent());
        aiChatConversationService.maybeRefreshSessionTitle(
                session.getAiChatSessionId(), summary, userId);
        aiChatSessionSummaryService.maybeRefreshSummaryAsync(
                session.getAiChatSessionId(), request.getAiLlmModelId());

        return ApiDesignResult.builder()
                .aiChatSessionId(session.getAiChatSessionId())
                .aiLlmModelId(modelConfig.getAiLlmModelId())
                .vendorName(modelConfig.getVendorName())
                .modelName(modelConfig.getModelName())
                .summary(summary)
                .thinkingContent(runResult.getThinkingContent())
                .patch(normalizedPatch)
                .validation(validation)
                .explainOnly(explainOnly)
                .toolTrace(runResult.getToolTrace())
                .interrupted(interruptedOrFailed)
                .build();
    }

    /** 确定写入会话与返回体的摘要文本：优先 patch.summary，其次模型正文。 */
    private static String resolveSummary(ApiDesignPatch patch, String content, boolean explainOnly) {
        if (patch != null && patch.getSummary() != null && !patch.getSummary().isBlank()) {
            return patch.getSummary().trim();
        }
        if (content != null && !content.isBlank()) {
            return content.trim();
        }
        return explainOnly ? "API 设计说明" : "AI 已生成修改建议";
    }

    /** 校验项目、接口、模型与用户输入是否齐全。 */
    private void validateRequest(ApiDesignRequest request) {
        if (request.getTestProjectId() == null) {
            throw new LlmClientException("缺少 testProjectId");
        }
        if (request.getTestProjectApiId() == null) {
            throw new LlmClientException("缺少 testProjectApiId");
        }
        if (request.getAiLlmModelId() == null) {
            throw new LlmClientException("请选择 AI 模型");
        }
        if (request.getPrompt() == null || request.getPrompt().isBlank()) {
            throw new LlmClientException("请输入需求描述");
        }
    }

    /** 构造会话锚点 JSON，将会话绑定到当前接口资产。 */
    private static String buildBizRefJson(Long testProjectApiId) {
        JSONObject obj = new JSONObject();
        obj.put("anchorType", "api_asset");
        obj.put("testProjectApiId", String.valueOf(testProjectApiId));
        return obj.toJSONString();
    }

    /**
     * 解析本轮是否开启思考链。
     * 已有会话优先用会话表 thinking_enabled；新建会话创建前用请求里的 thinkingEnabled；
     * 都没有则返回 null（跟随模型默认能力）。
     */
    private static Integer resolveSessionThinking(AiChatSession session, ApiDesignRequest request) {
        if (session.getThinkingEnabled() != null) {
            return session.getThinkingEnabled();
        }
        if (request.getThinkingEnabled() != null) {
            return request.getThinkingEnabled() ? 1 : 0;
        }
        return null;
    }

    /**
     * 组装首轮送入模型的消息。
     * 顺序：系统提示（全自动时追加全自动规程）→ 可选会话摘要 → 按 token 窗口裁剪的历史 → 本轮用户正文。
     * reservedTokens 预留系统提示、用户正文与摘要占用，避免历史装载挤爆上下文。
     */
    private List<ChatMessage> buildInitialMessages(ApiDesignRequest request, AiChatSession session,
                                                   LlmModelConfig modelConfig, boolean autopilot) {
        try {
            String systemPrompt = ApiDesignPromptResources.loadText(ApiDesignPromptResources.SYSTEM_PROMPT);
            if (autopilot) {
                systemPrompt = systemPrompt + "\n\n"
                        + ApiDesignPromptResources.loadText(ApiDesignPromptResources.AUTOPILOT_PROMPT);
            }
            String userContent = buildUserContent(request);
            int reservedTokens = TokenEstimator.estimateText(systemPrompt)
                    + TokenEstimator.estimateText(userContent);
            if (session.getContextSummary() != null && !session.getContextSummary().isBlank()) {
                reservedTokens += TokenEstimator.estimateText(session.getContextSummary()) + 20;
            }

            HistoryWindowPolicy policy = historyWindowPolicyResolver.resolve(modelConfig);
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(SystemMessage.from(systemPrompt));
            if (session.getContextSummary() != null && !session.getContextSummary().isBlank()) {
                messages.add(SystemMessage.from("【会话摘要】\n" + session.getContextSummary().trim()));
            }
            List<LlmMessage> history = aiChatConversationService.loadMessagesForLlm(
                    session.getAiChatSessionId(), modelConfig, policy, reservedTokens);
            messages.addAll(Lc4jMessageSupport.fromLlmMessages(history));
            messages.add(UserMessage.from(userContent));
            return messages;
        } catch (IOException e) {
            throw new LlmClientException("加载 AI Prompt 资源失败", e);
        }
    }

    /** 将项目/接口 id 与用户自然语言需求格式化为 user 消息正文。 */
    private static String buildUserContent(ApiDesignRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("【上下文】\n");
        sb.append("- testProjectId: ").append(request.getTestProjectId()).append('\n');
        sb.append("- testProjectApiId: ").append(request.getTestProjectApiId()).append('\n');
        sb.append("\n【用户描述】\n");
        sb.append(request.getPrompt().trim());
        return sb.toString();
    }
}
