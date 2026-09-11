package com.qualitest.ai.scenario.flow;

import com.alibaba.fastjson2.JSON;
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
import com.qualitest.ai.scenario.flow.model.DesignValidationResult;
import com.qualitest.ai.scenario.flow.model.FlowDesignPatch;
import com.qualitest.ai.scenario.flow.model.TestFlowDesignRequest;
import com.qualitest.ai.scenario.flow.model.TestFlowDesignResult;
import com.qualitest.ai.service.AiChatConversationService;
import com.qualitest.ai.service.AiChatSessionSummaryService;
import com.qualitest.ai.service.IAiLlmModelService;
import com.qualitest.ai.tools.AssetUpsertCapture;
import com.qualitest.ai.tools.AssetUpsertProposal;
import com.qualitest.ai.tools.FlowDesignPatchStats;
import com.qualitest.ai.tools.FlowDesignSubmitCapture;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.FlowDesignToolContextFactory;
import com.qualitest.ai.tools.FlowDesignToolExecutor;
import com.qualitest.ai.tools.FlowDesignToolsDefinitionService;
import com.qualitest.common.exception.ServiceException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 测试流 AI 设计编排：跑 Agent、收集画布修改建议与素材库写入提案、落库助手消息并返回结果。
 * <p>
 * 模型通过多次 submit_* 每次提交一个 Staging 单元；本类用 SubmitCapture 累积成功单元。
 * 本轮无一成功单元则 explainOnly=true（纯答疑，不灌 Staging）。
 * 步数耗尽但已有累积单元时仍返回已接受 patch，避免整轮作废。
 * 画布建议与素材提案都不自动写业务库，也不自动触发 Run。
 */
@Service
@RequiredArgsConstructor
public class TestFlowDesignAgent {

    private final IAiLlmModelService aiLlmModelService;
    private final AiLlmConfigService aiLlmConfigService;
    private final AiAgentRunner aiAgentRunner;
    private final FlowDesignToolExecutor flowDesignToolExecutor;
    private final FlowDesignToolContextFactory flowDesignToolContextFactory;
    private final FlowDesignToolsDefinitionService flowDesignToolsDefinitionService;
    private final AiChatConversationService aiChatConversationService;
    private final HistoryWindowPolicyResolver historyWindowPolicyResolver;
    private final AiChatSessionSummaryService aiChatSessionSummaryService;

    /**
     * 同步设计：执行完整 Agent 流程并返回结果。
     *
     * @param userId 当前登录用户 id，用于会话归属校验
     */
    public TestFlowDesignResult design(TestFlowDesignRequest request, Long userId) {
        return design(request, userId, null);
    }

    /**
     * 流式/同步共用：可选 {@link AgentRunListener} 推送 token 与 tool 事件。
     */
    public TestFlowDesignResult design(TestFlowDesignRequest request, Long userId, AgentRunListener listener) {
        return executeDesign(request, userId, listener);
    }

    /**
     * 执行一轮 AI 设计的核心流程。
     * <p>
     * 顺序：校验请求 → 加载/创建会话 → 注入 SubmitCapture 与素材提案容器 → Agent 循环
     * （开启步数将尽催 submit_*）→ 按是否有成功单元决定 explainOnly 与 patch →
     * 写助手消息 meta → 返回结果。
     */
    private TestFlowDesignResult executeDesign(TestFlowDesignRequest request,
                                               Long userId,
                                               AgentRunListener listener) {
        validateRequest(request);
        if (userId == null) {
            throw new ServiceException("未登录");
        }
        LlmModelConfig modelConfig = aiLlmModelService.resolve(request.getAiLlmModelId());

        String bizRef = buildBizRefJson(request);
        AiChatSession session = aiChatConversationService.loadOrCreate(
                request.getAiChatSessionId(),
                AiChatConversationService.SCENE_TEST_FLOW_DESIGN,
                request.isTemplateDesignMode() ? null : request.getTestProjectId(),
                bizRef,
                userId,
                request.getAiLlmModelId(),
                request.getPrompt(),
                request.getAiChatSessionId() == null ? request.getThinkingEnabled() : null);

        Integer sessionThinking = resolveSessionThinking(session, request);

        // 本轮内存容器：画布 patch 与素材库写入提案（工具只写容器，不落业务库）
        FlowDesignSubmitCapture submitCapture = new FlowDesignSubmitCapture();
        // 模板模式禁用素材 upsert（无真实项目素材库）
        AssetUpsertCapture assetUpsertCapture = request.isTemplateDesignMode() ? null : new AssetUpsertCapture();
        FlowDesignToolContext toolContext = flowDesignToolContextFactory.fromDesignRequest(
                request, submitCapture, assetUpsertCapture,
                session.getAiChatSessionId(),
                aiChatConversationService.loadFlowDesignClientIdMap(session.getAiChatSessionId()));

        List<Map<String, Object>> tools = flowDesignToolsDefinitionService.loadToolsDefinition();
        List<LlmMessage> messages = buildInitialMessages(request, session, modelConfig);

        aiChatConversationService.appendUserMessage(
                session.getAiChatSessionId(),
                request.getPrompt().trim(),
                buildUserMetaJson(request),
                request.getAiLlmModelId());

        AiAgentRunner.AgentRunResult runResult = aiAgentRunner.run(AiAgentRunner.AgentRunOptions.builder()
                .modelConfig(modelConfig)
                .initialMessages(messages)
                .tools(tools)
                .toolChoice("auto")
                .toolExecutor((name, args) -> flowDesignToolExecutor.executeTool(name, args, toolContext))
                .maxSteps(aiLlmConfigService.getMaxSteps())
                .listener(listener)
                .sessionThinkingEnabled(sessionThinking)
                .designSubmitNudgeEnabled(true)
                .build());

        if (!runResult.isOk()) {
            if (submitCapture.hasAccepted()
                    && runResult.getError() != null
                    && runResult.getError().contains("最大步数")) {
                // 已有累积单元：步数耗尽仍返回已接受 patch，避免整轮作废
            } else {
                throw new LlmClientException(runResult.getError());
            }
        }

        String content = runResult.getContent();
        // explainOnly：本轮未成功接受任何 submit_* 单元，视为纯答疑，无 patch
        boolean explainOnly = !submitCapture.hasAccepted();
        FlowDesignPatch normalizedPatch = explainOnly ? null : submitCapture.getNormalizedPatch();
        DesignValidationResult validation;

        if (explainOnly) {
            validation = DesignValidationResult.builder()
                    .ok(true)
                    .errors(List.of())
                    .warnings(List.of())
                    .build();
        } else {
            if (normalizedPatch == null) {
                throw new LlmClientException("submit_* 未产生有效 patch");
            }
            validation = submitCapture.getValidation();
            if (validation == null) {
                validation = DesignValidationResult.builder()
                        .ok(true)
                        .errors(List.of())
                        .warnings(List.of())
                        .build();
            }
        }

        String summary = resolveSummary(normalizedPatch, content, explainOnly);

        // 本轮 upsert 工具留下的素材提案（含明文 fields，供前端确认后落盘）
        List<AssetUpsertProposal> assetProposals = assetUpsertCapture.hasProposals()
                ? assetUpsertCapture.getProposals()
                : List.of();

        // 助手消息元数据：说明文案、是否仅答疑、画布 patch、素材提案、模型信息
        JSONObject meta = new JSONObject();
        meta.put("summary", summary);
        meta.put("explainOnly", explainOnly);
        meta.put("patchStats", FlowDesignPatchStats.build(normalizedPatch));
        meta.put("vendorName", modelConfig.getVendorName());
        meta.put("modelName", modelConfig.getModelName());
        if (!explainOnly && normalizedPatch != null) {
            meta.put("patchJson", normalizedPatch);
        }
        if (!assetProposals.isEmpty()) {
            meta.put("assetProposals", assetProposals);
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

        return TestFlowDesignResult.builder()
                .aiChatSessionId(session.getAiChatSessionId())
                .aiLlmModelId(modelConfig.getAiLlmModelId())
                .vendorName(modelConfig.getVendorName())
                .modelName(modelConfig.getModelName())
                .summary(summary)
                .thinkingContent(runResult.getThinkingContent())
                .patch(normalizedPatch)
                .validation(validation)
                .explainOnly(explainOnly)
                .assetProposals(assetProposals.isEmpty() ? null : assetProposals)
                .build();
    }

    /**
     * 确定展示给用户的 summary 文案。
     * 优先级：patch.summary → Agent 最终自然语言回复 → 默认占位文案。
     */
    private static String resolveSummary(FlowDesignPatch patch, String content, boolean explainOnly) {
        if (patch != null && patch.getSummary() != null && !patch.getSummary().isBlank()) {
            return patch.getSummary().trim();
        }
        if (content != null && !content.isBlank()) {
            return content.trim();
        }
        return explainOnly ? "流程说明" : "AI 已生成测试流修改建议";
    }

    private void validateRequest(TestFlowDesignRequest request) {
        if (request.isTemplateDesignMode()) {
            if (request.getAiLlmModelId() == null) {
                throw new LlmClientException("请选择 AI 模型");
            }
            if (request.getPrompt() == null || request.getPrompt().isBlank()) {
                throw new LlmClientException("请输入设计描述");
            }
            return;
        }
        if (request.getTestProjectId() == null) {
            throw new LlmClientException("缺少 testProjectId");
        }
        if (request.getTestFlowId() == null) {
            throw new LlmClientException("缺少 testFlowId");
        }
        if (request.getAiLlmModelId() == null) {
            throw new LlmClientException("请选择 AI 模型");
        }
        if (request.getPrompt() == null || request.getPrompt().isBlank()) {
            throw new LlmClientException("请输入设计描述");
        }
    }

    private static String buildBizRefJson(TestFlowDesignRequest request) {
        if (request.isTemplateDesignMode()) {
            String key = request.getTemplateFlowKey();
            if (key == null || key.isBlank()) {
                key = request.getTestFlowId() != null ? String.valueOf(request.getTestFlowId()) : "template";
            }
            return "{\"designMode\":\"template\",\"templateFlowKey\":\"" + escapeJson(key) + "\"}";
        }
        return buildBizRefJson(request.getTestFlowId());
    }

    private static String escapeJson(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String buildBizRefJson(Long testFlowId) {
        return "{\"testFlowId\":\"" + testFlowId + "\"}";
    }

    /** 会话库内开关优先；新会话创建前由 request.thinkingEnabled 落库 */
    private static Integer resolveSessionThinking(AiChatSession session, TestFlowDesignRequest request) {
        if (session.getThinkingEnabled() != null) {
            return session.getThinkingEnabled();
        }
        if (request.getThinkingEnabled() != null) {
            return request.getThinkingEnabled() ? 1 : 0;
        }
        return null;
    }

    /** 组装首轮送入模型的消息：system + 会话摘要 + 裁剪历史 + 本轮 user */
    private List<LlmMessage> buildInitialMessages(TestFlowDesignRequest request, AiChatSession session,
                                                  LlmModelConfig modelConfig) {
        try {
            String systemPrompt = FlowDesignPromptResources.loadText(FlowDesignPromptResources.SYSTEM_PROMPT);
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

    /** 构造送入 LLM 的 user 消息正文 */
    private static String buildUserContent(TestFlowDesignRequest request) {
        return AiDesignMentionSupport.buildUserLlmContent(
                request.getTestProjectId(),
                request.getTestFlowId(),
                request.getPrompt(),
                request.getMentions());
    }

    /**
     * 构建 user 消息 result_meta_json。
     * composerDoc 供前端恢复 chip；mentions 供多轮历史重建 LLM 上下文。
     */
    private static String buildUserMetaJson(TestFlowDesignRequest request) {
        boolean hasComposerDoc = request.getComposerDoc() != null && !request.getComposerDoc().isEmpty();
        boolean hasMentions = request.getMentions() != null && !request.getMentions().isEmpty();
        if (!hasComposerDoc && !hasMentions) {
            return null;
        }
        JSONObject meta = new JSONObject();
        if (hasComposerDoc) {
            meta.put("composerDoc", request.getComposerDoc());
        }
        if (hasMentions) {
            meta.put("mentions", request.getMentions());
        }
        return meta.toJSONString();
    }
}
