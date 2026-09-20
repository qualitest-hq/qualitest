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
import com.qualitest.ai.llm.lc4j.Lc4jMessageSupport;
import com.qualitest.ai.scenario.flow.model.DesignValidationResult;
import com.qualitest.ai.scenario.flow.model.FlowDesignPatch;
import com.qualitest.ai.scenario.flow.model.TestFlowDesignRequest;
import com.qualitest.ai.scenario.flow.model.TestFlowDesignResult;
import com.qualitest.ai.service.AiChatConversationService;
import com.qualitest.ai.service.AiChatSessionSummaryService;
import com.qualitest.ai.service.IAiLlmModelService;
import com.qualitest.ai.tools.AssetUpsertCapture;
import com.qualitest.ai.tools.AssetUpsertProposal;
import com.qualitest.ai.tools.AuthProfileUpsertCapture;
import com.qualitest.ai.tools.AuthProfileUpsertProposal;
import com.qualitest.ai.tools.FlowDesignPatchStats;
import com.qualitest.ai.tools.FlowDesignSubmitCapture;
import com.qualitest.ai.tools.FlowDesignToolContext;
import com.qualitest.ai.tools.FlowDesignToolContextFactory;
import com.qualitest.ai.tools.FlowDesignToolExecutor;
import com.qualitest.ai.tools.FlowDesignToolsDefinitionService;
import com.qualitest.ai.tools.flow.FlowDesignAutopilotCommitSupport;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.flow.validate.GraphJsonValidator;
import com.qualitest.project.service.ITestFlowService;
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
 * 测试流 AI 设计编排：跑工具循环、收集画布单元与素材/鉴权提案、写助手消息并返回结果。
 * <p>
 * 半自动（默认）：模型多次 submit_* 每次产出一个 Staging 单元，本类用 SubmitCapture 累积；
 * 本轮无成功单元则 explainOnly=true（纯答疑，不灌 Staging）；步数耗尽但已有单元时仍返回已接受 patch。
 * 画布、素材与鉴权 Profile 不自动写业务库，也不自动 Run；前端 Staging ✓ → 保存 → Run。
 * <p>
 * 全自动（请求 autopilotEnabled=true）：工具列表注入 run_test_flow；素材与鉴权 upsert 工具内直写；
 * 改图在 run 前或回合结束时隐式写入 test_flow；落盘失败抛 LlmClientException。
 * 模板设计模式强制关闭全自动。
 * <p>
 * 用户取消或断连时：步间停止，跳过回合末全自动写库，仍尽量写入助手半成品（正文、思考、工具轨迹、已接受 patch），并标 interrupted。
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
    private final ITestFlowService testFlowService;
    private final GraphJsonValidator graphJsonValidator;
    private final FlowDesignPatchNormalizer flowDesignPatchNormalizer;

    /**
     * 同步设计：执行完整 Agent 流程并返回结果。
     *
     * @param userId 当前登录用户 id，用于会话归属校验
     */
    public TestFlowDesignResult design(TestFlowDesignRequest request, Long userId) {
        return design(request, userId, null);
    }

    /**
     * 流式/同步共用入口。
     *
     * @param listener 可选；推送 token、思考链、工具起止、隐式落盘成功、Run 已触发等过程事件
     */
    public TestFlowDesignResult design(TestFlowDesignRequest request, Long userId, AgentRunListener listener) {
        return executeDesign(request, userId, listener, null);
    }

    /**
     * 流式入口：可传入取消标志。
     *
     * @param cancelled 用户取消或 SSE 断连后为 true；工具循环在步间停止并把已有结果写入助手消息
     */
    public TestFlowDesignResult design(TestFlowDesignRequest request, Long userId,
                                       AgentRunListener listener, BooleanSupplier cancelled) {
        return executeDesign(request, userId, listener, cancelled);
    }

    /**
     * 执行一轮测试流 AI 设计。
     * <p>
     * 校验请求 → 加载或创建会话 → 跑工具循环 → 按是否有成功 submit 决定 explainOnly 与 patch →
     * 写助手消息元数据并返回。若本轮被取消，跳过全自动回合末写库，仍尽量写入助手半成品（正文、思考、轨迹、已接受的 patch）。
     */
    private TestFlowDesignResult executeDesign(TestFlowDesignRequest request,
                                               Long userId,
                                               AgentRunListener listener,
                                               BooleanSupplier cancelled) {
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

        if (listener != null && session.getAiChatSessionId() != null) {
            // 会话就绪后立刻回调，便于流式接口尽早推送 session 事件
            listener.onSessionReady(session.getAiChatSessionId());
        }

        Integer sessionThinking = resolveSessionThinking(session, request);

        // 本轮内存容器：画布 patch 与素材/多端 Profile 提案（工具只写容器，不落业务库）
        FlowDesignSubmitCapture submitCapture = new FlowDesignSubmitCapture();
        // 模板模式禁用素材/多端 Profile upsert（无真实项目库）
        AssetUpsertCapture assetUpsertCapture = request.isTemplateDesignMode() ? null : new AssetUpsertCapture();
        AuthProfileUpsertCapture authProfileUpsertCapture =
                request.isTemplateDesignMode() ? null : new AuthProfileUpsertCapture();
        boolean autopilot = request.isAutopilotEnabledEffective();
        FlowDesignToolContext toolContext = flowDesignToolContextFactory.fromDesignRequest(
                request, submitCapture, assetUpsertCapture, authProfileUpsertCapture,
                session.getAiChatSessionId(),
                aiChatConversationService.loadFlowDesignClientIdMap(session.getAiChatSessionId()),
                autopilot,
                (flowId, graph) -> {
                    // 隐式写库成功：推送 testFlowId，画布可清 Staging 并重载
                    if (listener != null && flowId != null) {
                        listener.onGraphCommitted(flowId);
                    }
                },
                runId -> {
                    // Run 已触发：推送 runId，画布可开始按步骤高亮
                    if (listener != null && runId != null) {
                        listener.onRunStarted(runId);
                    }
                });

        List<Map<String, Object>> tools = flowDesignToolsDefinitionService.loadToolsDefinition(autopilot);
        List<ChatMessage> messages = buildInitialMessages(request, session, modelConfig, autopilot);

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
                .cancelled(cancelled)
                .build());

        boolean interrupted = runResult.isInterrupted();
        if (!runResult.isOk() && !interrupted) {
            if (submitCapture.hasAccepted()
                    && runResult.getError() != null
                    && runResult.getError().contains("最大步数")) {
                // 已有累积单元：步数耗尽仍返回已接受 patch，避免整轮作废
            } else {
                // 硬失败：尽量落盘已有轨迹与半成品后再抛
                persistPartialAssistant(request, userId, session, modelConfig, runResult,
                        submitCapture, assetUpsertCapture, authProfileUpsertCapture, true);
                throw new LlmClientException(runResult.getError());
            }
        }

        // 全自动：仅正常结束时补做回合末隐式写库；中断跳过以免半成品强行落库
        if (!interrupted && autopilot && submitCapture.hasAccepted()) {
            FlowDesignAutopilotCommitSupport.CommitOutcome outcome =
                    FlowDesignAutopilotCommitSupport.commitIfNeeded(
                            toolContext, testFlowService, graphJsonValidator, flowDesignPatchNormalizer);
            if (!outcome.ok()) {
                String detail = outcome.errors().isEmpty()
                        ? outcome.message()
                        : outcome.message() + " — " + String.join("; ", outcome.errors());
                throw new LlmClientException("全自动落盘失败: " + detail);
            }
        }

        return persistPartialAssistant(request, userId, session, modelConfig, runResult,
                submitCapture, assetUpsertCapture, authProfileUpsertCapture, interrupted);
    }

    /**
     * 将本轮助手结果写入会话并组装返回体。
     * 中断或硬失败时：摘要优先用已有正文，否则用错误说明或「本轮已中断」；元数据带 interrupted=true，并写入已有 toolTrace 与已接受的 patch。
     *
     * @param interruptedOrFailed true 表示本轮未正常收尾（取消或失败后的半成品落盘）
     */
    private TestFlowDesignResult persistPartialAssistant(
            TestFlowDesignRequest request,
            Long userId,
            AiChatSession session,
            LlmModelConfig modelConfig,
            AiAgentRunner.AgentRunResult runResult,
            FlowDesignSubmitCapture submitCapture,
            AssetUpsertCapture assetUpsertCapture,
            AuthProfileUpsertCapture authProfileUpsertCapture,
            boolean interruptedOrFailed) {
        String content = runResult.getContent();
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
            if (normalizedPatch == null && !interruptedOrFailed) {
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

        String summary;
        if (interruptedOrFailed) {
            summary = AiAgentRunner.resolveInterruptedSummary(content, runResult.getError());
        } else {
            summary = resolveSummary(normalizedPatch, content, explainOnly);
        }

        List<AssetUpsertProposal> assetProposals = assetUpsertCapture != null && assetUpsertCapture.hasProposals()
                ? assetUpsertCapture.getProposals()
                : List.of();
        List<AuthProfileUpsertProposal> authProfileProposals =
                authProfileUpsertCapture != null && authProfileUpsertCapture.hasProposals()
                        ? authProfileUpsertCapture.getProposals()
                        : List.of();

        JSONObject meta = new JSONObject();
        meta.put("summary", summary);
        meta.put("explainOnly", explainOnly);
        meta.put("patchStats", FlowDesignPatchStats.build(normalizedPatch));
        meta.put("vendorName", modelConfig.getVendorName());
        meta.put("modelName", modelConfig.getModelName());
        if (interruptedOrFailed) {
            meta.put("interrupted", true);
        }
        if (!explainOnly && normalizedPatch != null) {
            meta.put("patchJson", normalizedPatch);
        }
        if (!assetProposals.isEmpty()) {
            meta.put("assetProposals", assetProposals);
        }
        if (!authProfileProposals.isEmpty()) {
            meta.put("authProfileProposals", authProfileProposals);
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
                .authProfileProposals(authProfileProposals.isEmpty() ? null : authProfileProposals)
                .toolTrace(runResult.getToolTrace())
                .interrupted(interruptedOrFailed)
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

    /**
     * 解析本轮是否开启思考链。
     * 已有会话优先用会话表 thinking_enabled；新建会话创建前用请求里的 thinkingEnabled；
     * 都没有则返回 null（跟随模型默认能力）。
     */
    private static Integer resolveSessionThinking(AiChatSession session, TestFlowDesignRequest request) {
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
    private List<ChatMessage> buildInitialMessages(TestFlowDesignRequest request, AiChatSession session,
                                                   LlmModelConfig modelConfig, boolean autopilot) {
        try {
            String systemPrompt = FlowDesignPromptResources.loadText(FlowDesignPromptResources.SYSTEM_PROMPT);
            if (autopilot) {
                systemPrompt = systemPrompt + "\n\n"
                        + FlowDesignPromptResources.loadText(FlowDesignPromptResources.AUTOPILOT_PROMPT);
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

    /**
     * 构造送入 LLM 的 user 消息正文。
     * 若请求带 runRiskWarnings，追加「运行风险预检」段落，提示先处理 AUTH_* 等问题。
     */
    private static String buildUserContent(TestFlowDesignRequest request) {
        String base = AiDesignMentionSupport.buildUserLlmContent(
                request.getTestProjectId(),
                request.getTestFlowId(),
                request.getPrompt(),
                request.getMentions());
        List<String> risks = request.getRunRiskWarnings();
        if (risks == null || risks.isEmpty()) {
            return base;
        }
        StringBuilder sb = new StringBuilder(base);
        sb.append("\n\n【运行风险预检（须处理）】\n");
        for (String risk : risks) {
            if (risk != null && !risk.isBlank()) {
                sb.append("- ").append(risk.trim()).append('\n');
            }
        }
        sb.append("若含 AUTH_TOKEN_MISSING：先 list_project_auth_profiles 判断 Profile 是否绑错端；")
                .append("绑错则 upsert_auth_profile，缺登录抽取则 submit 补 extracts / 登录子流。");
        return sb.toString();
    }

    /**
     * 构建 user 消息 result_meta_json。
     * 写入 composerDoc（恢复编辑器 chip）与 mentions（多轮历史重建 LLM 上下文）。
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
