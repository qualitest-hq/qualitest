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

/**
 * AI API 助手场景编排：会话、工具调用、产出设计 patch。
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
        return design(request, userId, null);
    }

    /**
     * 执行一轮 API 设计对话：加载会话与工具、调用大模型、持久化助手消息并返回 patch 或纯说明。
     *
     * @param listener 可选；非空时推送 token、思考链与工具调用事件
     */
    public ApiDesignResult design(ApiDesignRequest request, Long userId, AgentRunListener listener) {
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

        Integer sessionThinking = resolveSessionThinking(session, request);

        ApiDesignSubmitCapture submitCapture = new ApiDesignSubmitCapture();
        ApiDesignToolContext toolContext = apiDesignToolContextFactory.fromDesignRequest(request, submitCapture);

        List<Map<String, Object>> tools = apiDesignToolsDefinitionService.loadToolsDefinition();
        boolean autopilot = request.isAutopilotEnabledEffective();
        List<LlmMessage> messages = buildInitialMessages(request, session, modelConfig, autopilot);

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
                .build());

        if (!runResult.isOk()) {
            throw new LlmClientException(runResult.getError());
        }

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
            if (normalizedPatch == null) {
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

        String summary = resolveSummary(normalizedPatch, content, explainOnly);

        JSONObject meta = new JSONObject();
        meta.put("summary", summary);
        meta.put("explainOnly", explainOnly);
        meta.put("vendorName", modelConfig.getVendorName());
        meta.put("modelName", modelConfig.getModelName());
        if (!explainOnly && normalizedPatch != null) {
            meta.put("patchJson", normalizedPatch);
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

    /** 解析本轮是否开启思考链：已有会话取会话配置，新建会话取请求参数。 */
    private static Integer resolveSessionThinking(AiChatSession session, ApiDesignRequest request) {
        if (session.getThinkingEnabled() != null) {
            return session.getThinkingEnabled();
        }
        if (request.getThinkingEnabled() != null) {
            return request.getThinkingEnabled() ? 1 : 0;
        }
        return null;
    }

    /** 组装首轮 LLM 消息：系统提示（可含全自动段）、会话摘要、历史窗口与当前用户描述。 */
    private List<LlmMessage> buildInitialMessages(ApiDesignRequest request, AiChatSession session,
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
            List<LlmMessage> messages = new ArrayList<>();
            messages.add(LlmMessage.system(systemPrompt));
            if (session.getContextSummary() != null && !session.getContextSummary().isBlank()) {
                messages.add(LlmMessage.system("【会话摘要】\n" + session.getContextSummary().trim()));
            }
            messages.addAll(aiChatConversationService.loadMessagesForLlm(
                    session.getAiChatSessionId(), modelConfig, policy, reservedTokens));
            messages.add(LlmMessage.user(userContent));
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
