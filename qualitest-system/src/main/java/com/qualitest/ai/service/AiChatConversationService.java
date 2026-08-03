package com.qualitest.ai.service;

import cn.hutool.core.util.IdUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.domain.AiChatMessage;
import com.qualitest.ai.scenario.flow.AiDesignMentionSupport;
import com.qualitest.ai.domain.AiChatSession;
import com.qualitest.ai.llm.LlmMessage;
import com.qualitest.ai.llm.LlmModelConfig;
import com.qualitest.ai.llm.history.ChatHistoryTrimmer;
import com.qualitest.ai.llm.history.HistoryWindowPolicy;
import com.qualitest.ai.mapper.AiChatMessageMapper;
import com.qualitest.ai.params.AiChatSessionParams;
import com.qualitest.ai.params.CreateAiChatSessionRequest;
import com.qualitest.ai.result.AiChatMessageResult;
import com.qualitest.ai.result.AiChatSessionDetailResult;
import com.qualitest.ai.result.AiChatSessionResult;
import com.qualitest.ai.tools.support.AssetUpsertSupport;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.common.utils.DateUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

/**
 * 画布侧 AI 多轮会话业务服务。
 * <p>
 * 负责会话的创建、查询、删除与消息落库；与 admin CRUD 分离，按 userId 校验归属。
 */
@Service
@RequiredArgsConstructor
public class AiChatConversationService {

    /** 测试流设计场景标识 */
    public static final String SCENE_TEST_FLOW_DESIGN = "test_flow_design";

    /** AI API 助手场景（接口设计：约束/测值/脚本统一） */
    public static final String SCENE_TEST_API_DESIGN = "test_api_design";

    private static final int DEFAULT_LLM_HISTORY_LIMIT = 10;

    /** 前端会话消息分页默认条数 */
    public static final int DEFAULT_MESSAGE_PAGE_SIZE = 40;

    private final IAiChatSessionService aiChatSessionService;
    private final IAiChatMessageService aiChatMessageService;
    private final AiChatMessageMapper aiChatMessageMapper;

    /**
     * 列出当前用户在指定场景与业务锚点下的会话，按创建时间降序。
     */
    public List<AiChatSessionResult> listSessions(String scene, Long testProjectId, Long testFlowId, Long userId) {
        return listSessions(scene, testProjectId, testFlowId, null, userId);
    }

    /**
     * 列出当前用户在指定场景与业务锚点下的会话，按创建时间降序。
     */
    public List<AiChatSessionResult> listSessions(String scene, Long testProjectId, Long testFlowId,
                                                  Long testProjectApiId, Long userId) {
        AiChatSessionParams params = AiChatSessionParams.builder()
                .sessionScene(scene)
                .testProjectId(testProjectId)
                .testFlowId(testFlowId)
                .testProjectApiId(testProjectApiId)
                .userId(userId)
                .build();
        List<AiChatSessionResult> list = aiChatSessionService.selectAiChatSessionResultList(params);
        list.sort(Comparator.comparing(AiChatSessionResult::getCreateTime,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return list;
    }

    /**
     * 获取会话详情及全部消息（升序）。校验会话归属当前用户。
     */
    public AiChatSessionDetailResult getSessionWithMessages(Long sessionId, Long userId) {
        return getSessionWithMessages(sessionId, userId, null, null);
    }

    /**
     * 获取会话详情及消息（升序，可选分页）。limit 为空或 ≤0 时返回全部消息。
     *
     * @param beforeMessageId 游标：加载该 id 之前的更早消息；为空时加载最新 limit 条
     */
    public AiChatSessionDetailResult getSessionWithMessages(
            Long sessionId, Long userId, Integer limit, Long beforeMessageId) {
        requireOwnedSession(sessionId, userId);
        AiChatSessionResult meta = aiChatSessionService.selectAiChatSessionResult(sessionId);
        return buildSessionDetail(sessionId, meta, limit, beforeMessageId, false);
    }

    /**
     * 获取会话详情及消息摘要。
     * 摘要中去掉 patchJson 全文与素材提案 fields 明文，改标 hasPatch / hasAssetProposals。
     */
    public AiChatSessionDetailResult getSessionWithMessageSummaries(Long sessionId, Long userId) {
        return getSessionWithMessageSummaries(sessionId, userId, null, null);
    }

    /**
     * 获取会话详情及消息摘要（可选分页）。
     */
    public AiChatSessionDetailResult getSessionWithMessageSummaries(
            Long sessionId, Long userId, Integer limit, Long beforeMessageId) {
        requireOwnedSession(sessionId, userId);
        AiChatSessionResult meta = aiChatSessionService.selectAiChatSessionResult(sessionId);
        return buildSessionDetail(sessionId, meta, limit, beforeMessageId, true);
    }

    private AiChatSessionDetailResult buildSessionDetail(
            Long sessionId,
            AiChatSessionResult meta,
            Integer limit,
            Long beforeMessageId,
            boolean summaryMode) {
        List<AiChatMessageResult> raw = loadSessionMessages(sessionId, limit, beforeMessageId);
        List<AiChatMessageResult> messages = summaryMode ? summarizeMessages(raw) : raw;
        AiChatSessionDetailResult.AiChatSessionDetailResultBuilder builder = AiChatSessionDetailResult.builder()
                .session(meta)
                .messages(messages);
        if (limit != null && limit > 0) {
            int total = aiChatMessageMapper.countAiChatMessageBySessionId(sessionId);
            builder.totalMessageCount(total);
            builder.hasMoreOlder(resolveHasMoreOlder(sessionId, raw, limit, beforeMessageId, total));
        }
        return builder.build();
    }

    private List<AiChatMessageResult> loadSessionMessages(Long sessionId, Integer limit, Long beforeMessageId) {
        if (limit == null || limit <= 0) {
            return aiChatMessageMapper.selectAiChatMessageResultListBySessionAsc(sessionId);
        }
        return aiChatMessageMapper.selectAiChatMessageResultPageBySession(sessionId, beforeMessageId, limit);
    }

    private List<AiChatMessageResult> summarizeMessages(List<AiChatMessageResult> raw) {
        List<AiChatMessageResult> summaries = new ArrayList<>(raw.size());
        for (AiChatMessageResult message : raw) {
            summaries.add(toMessageSummary(message));
        }
        return summaries;
    }

    private boolean resolveHasMoreOlder(
            Long sessionId,
            List<AiChatMessageResult> page,
            int limit,
            Long beforeMessageId,
            int totalCount) {
        if (page.isEmpty()) {
            return false;
        }
        if (beforeMessageId == null) {
            return totalCount > page.size();
        }
        Long oldestLoadedId = page.get(0).getAiChatMessageId();
        return aiChatMessageMapper.countAiChatMessageBeforeId(sessionId, oldestLoadedId) > 0;
    }

    /**
     * 按 id 取单条消息完整元数据（含 patchJson、素材提案 fields）。
     * 校验消息所属会话归当前用户所有。
     */
    public AiChatMessageResult getMessageMeta(Long messageId, Long userId) {
        if (messageId == null) {
            throw new ServiceException("缺少 messageId");
        }
        AiChatMessageResult message = aiChatMessageMapper.selectAiChatMessageResult(messageId);
        if (message == null) {
            throw new ServiceException("消息不存在");
        }
        requireOwnedSession(message.getAiChatSessionId(), userId);
        return message;
    }

    /**
     * 更新助手消息的 resultMetaJson（例如素材提案确认/拒绝后改写 status）。
     * 须为助手角色消息，且会话归属当前用户。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateAssistantResultMeta(Long messageId, Long userId, String resultMetaJson) {
        AiChatMessageResult message = getMessageMeta(messageId, userId);
        if (!"assistant".equals(message.getMessageRole())) {
            throw new ServiceException("仅可更新助手消息元数据");
        }
        AiChatMessage update = AiChatMessage.builder()
                .aiChatMessageId(messageId)
                .resultMetaJson(resultMetaJson)
                .build();
        aiChatMessageService.updateAiChatMessage(update);
    }

    /**
     * 把助手消息压成列表用摘要：去掉 patchJson，标 hasPatch；
     * 素材提案去掉 fields 明文、保留 fieldNames，标 hasAssetProposals。
     */
    static AiChatMessageResult toMessageSummary(AiChatMessageResult message) {
        if (message == null || !"assistant".equals(message.getMessageRole())) {
            return message;
        }
        String metaJson = message.getResultMetaJson();
        if (metaJson == null || metaJson.isBlank()) {
            return message;
        }
        try {
            JSONObject meta = JSON.parseObject(metaJson);
            boolean explainOnly = Boolean.TRUE.equals(meta.getBoolean("explainOnly"));
            boolean hasPatch = !explainOnly && meta.containsKey("patchJson") && meta.get("patchJson") != null;
            meta.remove("patchJson");
            if (hasPatch) {
                meta.put("hasPatch", true);
            }
            redactAssetProposalsForSummary(meta);
            return AiChatMessageResult.builder()
                    .aiChatMessageId(message.getAiChatMessageId())
                    .aiChatSessionId(message.getAiChatSessionId())
                    .messageRole(message.getMessageRole())
                    .aiLlmModelId(message.getAiLlmModelId())
                    .messageContent(message.getMessageContent())
                    .thinkingContent(message.getThinkingContent())
                    .resultMetaJson(meta.toJSONString())
                    .createTime(message.getCreateTime())
                    .build();
        } catch (Exception ignored) {
            return message;
        }
    }

    /**
     * 列表摘要场景：assetProposals 去掉 fields 明文，只留 key、action、remark、status、fieldNames，
     * 并设置 hasAssetProposals=true。无有效提案则移除该字段。
     */
    static void redactAssetProposalsForSummary(JSONObject meta) {
        if (meta == null || !meta.containsKey("assetProposals")) {
            return;
        }
        Object raw = meta.get("assetProposals");
        if (!(raw instanceof JSONArray arr) || arr.isEmpty()) {
            meta.remove("assetProposals");
            return;
        }
        JSONArray safe = new JSONArray();
        for (int i = 0; i < arr.size(); i++) {
            JSONObject item = arr.getJSONObject(i);
            if (item == null) {
                continue;
            }
            JSONObject copy = new JSONObject();
            copy.put("key", item.getString("key"));
            if (item.getString("action") != null) {
                copy.put("action", item.getString("action"));
            }
            if (item.getString("remark") != null) {
                copy.put("remark", item.getString("remark"));
            }
            String status = item.getString("status");
            copy.put("status", status != null ? status : "pending");
            JSONArray fieldNames = AssetUpsertSupport.fieldNamesArrayFromFieldsObj(item.get("fields"));
            if (fieldNames.isEmpty() && item.getJSONArray("fieldNames") != null) {
                fieldNames = item.getJSONArray("fieldNames");
            }
            copy.put("fieldNames", fieldNames);
            safe.add(copy);
        }
        meta.put("assetProposals", safe);
        meta.put("hasAssetProposals", true);
    }

    /**
     * 读取会话所属项目 id（调用方需已校验会话归属）。
     */
    public Long getSessionTestProjectId(Long sessionId) {
        AiChatSession session = aiChatSessionService.selectAiChatSessionById(sessionId);
        return session != null ? session.getTestProjectId() : null;
    }

    /**
     * 新建空会话。
     */
    @Transactional(rollbackFor = Exception.class)
    public AiChatSessionResult createSession(CreateAiChatSessionRequest request, Long userId) {
        Date now = DateUtils.getNowDate();
        AiChatSession session = AiChatSession.builder()
                .aiChatSessionId(IdUtil.getSnowflakeNextId())
                .testProjectId(request.getTestProjectId())
                .userId(userId)
                .sessionScene(request.getSessionScene())
                .bizRefJson(request.getBizRefJson())
                .currentModelId(request.getCurrentModelId())
                .sessionTitle(request.getSessionTitle())
                .delStatus(0)
                .build();
        if (request.getThinkingEnabled() != null) {
            session.setThinkingEnabled(request.getThinkingEnabled() ? 1 : 0);
        }
        session.setCreateTime(now);
        aiChatSessionService.insertAiChatSession(session);
        return aiChatSessionService.selectAiChatSessionResult(session.getAiChatSessionId());
    }

    /**
     * 更新会话绑定的模型 id。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateSessionModel(Long sessionId, Long modelId, Long userId) {
        requireOwnedSession(sessionId, userId);
        AiChatSession update = new AiChatSession();
        update.setAiChatSessionId(sessionId);
        update.setCurrentModelId(modelId);
        aiChatSessionService.updateAiChatSession(update);
    }

    /** 更新会话思考开关 */
    @Transactional(rollbackFor = Exception.class)
    public void updateSessionThinking(Long sessionId, boolean thinkingEnabled, Long userId) {
        requireOwnedSession(sessionId, userId);
        AiChatSession update = new AiChatSession();
        update.setAiChatSessionId(sessionId);
        update.setThinkingEnabled(thinkingEnabled ? 1 : 0);
        aiChatSessionService.updateAiChatSession(update);
    }

    /**
     * 从锚点消息起截断会话后续消息。
     *
     * @param inclusive true 时连同锚点消息一并删除；false 时仅删除锚点之后的消息
     * @return 实际删除的消息条数
     */
    @Transactional(rollbackFor = Exception.class)
    public int truncateMessagesAfter(Long sessionId, Long anchorMessageId, boolean inclusive, Long userId) {
        requireOwnedSession(sessionId, userId);
        if (anchorMessageId == null) {
            throw new ServiceException("缺少 anchorMessageId");
        }
        List<AiChatMessageResult> all = aiChatMessageMapper.selectAiChatMessageResultListBySessionAsc(sessionId);
        int anchorIdx = -1;
        for (int i = 0; i < all.size(); i++) {
            if (anchorMessageId.equals(all.get(i).getAiChatMessageId())) {
                anchorIdx = i;
                break;
            }
        }
        if (anchorIdx < 0) {
            throw new ServiceException("消息不存在或不属于该会话");
        }
        int fromIdx = inclusive ? anchorIdx : anchorIdx + 1;
        if (fromIdx >= all.size()) {
            return 0;
        }
        List<Long> ids = new ArrayList<>();
        for (int i = fromIdx; i < all.size(); i++) {
            ids.add(all.get(i).getAiChatMessageId());
        }
        if (ids.isEmpty()) {
            return 0;
        }
        return aiChatMessageMapper.deleteAiChatMessageByIdList(ids);
    }

    /**
     * 首轮 assistant 回复后，用 summary 刷新仍为占位/截断 prompt 的会话标题。
     */
    @Transactional(rollbackFor = Exception.class)
    public void maybeRefreshSessionTitle(Long sessionId, String summary, Long userId) {
        if (summary == null || summary.isBlank()) {
            return;
        }
        AiChatSession session = requireOwnedSession(sessionId, userId);
        List<AiChatMessageResult> all = aiChatMessageMapper.selectAiChatMessageResultListBySessionAsc(sessionId);
        long assistantCount = all.stream()
                .filter(m -> "assistant".equals(m.getMessageRole()))
                .count();
        if (assistantCount != 1) {
            return;
        }
        String currentTitle = session.getSessionTitle();
        if (!shouldRefreshSessionTitle(currentTitle, all)) {
            return;
        }
        AiChatSession update = new AiChatSession();
        update.setAiChatSessionId(sessionId);
        update.setSessionTitle(buildTitleFromSummary(summary));
        aiChatSessionService.updateAiChatSession(update);
    }

    /**
     * 软删除会话：标记 del_status=1，保留消息数据。
     */
    @Transactional(rollbackFor = Exception.class)
    public void deleteSession(Long sessionId, Long userId) {
        requireOwnedSession(sessionId, userId);
        AiChatSession update = new AiChatSession();
        update.setAiChatSessionId(sessionId);
        update.setDelStatus(1);
        aiChatSessionService.updateAiChatSession(update);
    }

    /**
     * 加载已有会话或创建新会话。
     * sessionId 为空时按场景与业务锚点新建；非空时校验归属。
     */
    @Transactional(rollbackFor = Exception.class)
    public AiChatSession loadOrCreate(Long sessionId, String scene, Long testProjectId,
                                      String bizRefJson, Long userId, Long modelId, String titleHint) {
        return loadOrCreate(sessionId, scene, testProjectId, bizRefJson, userId, modelId, titleHint, null);
    }

    /**
     * 加载已有会话或创建新会话。
     * {@code thinkingEnabledOnCreate} 仅在新会话时写入 thinking_enabled。
     */
    @Transactional(rollbackFor = Exception.class)
    public AiChatSession loadOrCreate(Long sessionId, String scene, Long testProjectId,
                                      String bizRefJson, Long userId, Long modelId, String titleHint,
                                      Boolean thinkingEnabledOnCreate) {
        if (sessionId != null) {
            AiChatSession session = requireOwnedSession(sessionId, userId);
            validateSessionBizRef(session, bizRefJson);
            return session;
        }
        String title = buildSessionTitle(titleHint);
        CreateAiChatSessionRequest req = new CreateAiChatSessionRequest();
        req.setSessionScene(scene);
        req.setTestProjectId(testProjectId);
        req.setBizRefJson(bizRefJson);
        req.setCurrentModelId(modelId);
        req.setSessionTitle(title);
        req.setThinkingEnabled(thinkingEnabledOnCreate);
        AiChatSessionResult created = createSession(req, userId);
        return aiChatSessionService.selectAiChatSessionById(created.getAiChatSessionId());
    }

    /**
     * 追加一条 user 消息（无结构化元数据）。
     */
    @Transactional(rollbackFor = Exception.class)
    public void appendUserMessage(Long sessionId, String content, Long modelId) {
        appendUserMessage(sessionId, content, null, modelId);
    }

    /**
     * 追加一条 user 消息。
     *
     * @param userMetaJson 可选元数据 JSON，如 composerDoc，写入 result_meta_json
     */
    @Transactional(rollbackFor = Exception.class)
    public void appendUserMessage(Long sessionId, String content, String userMetaJson, Long modelId) {
        insertMessage(sessionId, "user", content, null, userMetaJson, modelId);
    }

    @Transactional(rollbackFor = Exception.class)
    public void appendAssistantMessage(Long sessionId, String messageContent, String resultMeta, Long modelId) {
        appendAssistantMessage(sessionId, messageContent, resultMeta, modelId, null);
    }

    /**
     * 追加一条 assistant 消息。
     * <p>
     * 测试流设计场景下：
     * <ul>
     *   <li>{@code messageContent} — 用户可见的自然语言 summary</li>
     *   <li>{@code thinkingContent} — 模型思考过程，独立字段存储，不送入多轮 LLM</li>
 *   <li>{@code resultMeta} — JSON：summary、explainOnly、patchStats、vendorName、modelName；
 *       有画布建议时含 patchJson；有素材库写入提案时含 assetProposals（含 fields）</li>
     * </ul>
     *
     * @param messageContent  assistant 自然语言正文
     * @param resultMeta      结构化元数据 JSON 字符串
     * @param thinkingContent 思考过程全文，可为 null
     */
    @Transactional(rollbackFor = Exception.class)
    public void appendAssistantMessage(Long sessionId, String messageContent, String resultMeta, Long modelId,
                                       String thinkingContent) {
        insertMessage(sessionId, "assistant", messageContent, thinkingContent, resultMeta, modelId);
    }

    /**
     * 加载送入 LLM 的多轮历史（兼容旧调用：仅按条数截断）。
     *
     * @deprecated 请使用 {@link #loadMessagesForLlm(Long, LlmModelConfig, HistoryWindowPolicy, int)}
     */
    @Deprecated
    public List<LlmMessage> loadMessagesForLlm(Long sessionId, int limit) {
        List<LlmMessage> full = buildFullLlmHistory(sessionId);
        int effectiveLimit = limit > 0 ? limit : DEFAULT_LLM_HISTORY_LIMIT;
        if (full.size() <= effectiveLimit) {
            return full;
        }
        return new ArrayList<>(full.subList(full.size() - effectiveLimit, full.size()));
    }

    /**
     * 加载送入 LLM 的多轮历史，按条数与 token 预算裁剪。
     *
     * @param reservedTokens 为 system、会话摘要、本轮 user 等预留的 token
     */
    public List<LlmMessage> loadMessagesForLlm(Long sessionId, LlmModelConfig modelConfig,
                                               HistoryWindowPolicy policy, int reservedTokens) {
        List<LlmMessage> full = buildFullLlmHistory(sessionId);
        if (policy == null) {
            return full;
        }
        return ChatHistoryTrimmer.trim(full, policy, reservedTokens);
    }

    /** 构建会话完整 LLM 历史（升序，未裁剪） */
    public List<LlmMessage> buildFullLlmHistory(Long sessionId) {
        AiChatSession session = aiChatSessionService.selectAiChatSessionById(sessionId);
        Long testProjectId = session != null ? session.getTestProjectId() : null;
        Long testFlowId = session != null ? parseTestFlowId(session.getBizRefJson()) : null;

        List<AiChatMessageResult> all = aiChatMessageMapper.selectAiChatMessageResultListBySessionAsc(sessionId);
        List<LlmMessage> history = new ArrayList<>();
        for (AiChatMessageResult msg : all) {
            if ("user".equals(msg.getMessageRole())) {
                history.add(LlmMessage.user(formatUserMessageForLlm(msg, testProjectId, testFlowId)));
            } else if ("assistant".equals(msg.getMessageRole())) {
                String summary = extractAssistantSummary(msg);
                if (summary != null && !summary.isBlank()) {
                    history.add(LlmMessage.assistant(summary, null));
                }
            }
        }
        return history;
    }

    /** 统计会话 assistant 消息条数 */
    public int countAssistantMessages(Long sessionId) {
        List<AiChatMessageResult> all = aiChatMessageMapper.selectAiChatMessageResultListBySessionAsc(sessionId);
        int count = 0;
        for (AiChatMessageResult msg : all) {
            if ("assistant".equals(msg.getMessageRole())) {
                count++;
            }
        }
        return count;
    }

    /** 统计会话消息总条数 */
    public int countSessionMessages(Long sessionId) {
        return aiChatMessageMapper.countAiChatMessageBySessionId(sessionId);
    }

    /**
     * 更新会话滚动摘要 Checkpoint。
     */
    @Transactional(rollbackFor = Exception.class)
    public void updateContextSummary(Long sessionId, String summary, int coveredMessageCount) {
        AiChatSession update = new AiChatSession();
        update.setAiChatSessionId(sessionId);
        update.setContextSummary(summary);
        update.setSummaryMessageCount(coveredMessageCount);
        update.setSummaryUpdatedAt(DateUtils.getNowDate());
        aiChatSessionService.updateAiChatSession(update);
    }

    /**
     * 将被窗口裁掉的早期消息格式化为摘要输入文本。
     */
    public String formatMessagesForSummary(List<LlmMessage> messages) {
        if (messages == null || messages.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (LlmMessage msg : messages) {
            if (msg.getContent() == null || msg.getContent().isBlank()) {
                continue;
            }
            sb.append(msg.getRole()).append(": ").append(msg.getContent().trim()).append('\n');
        }
        return sb.toString().trim();
    }

    /**
     * 获取被历史窗口裁掉的头部消息，供 Checkpoint 摘要使用。
     */
    public List<LlmMessage> loadDroppedHistoryForSummary(Long sessionId, LlmModelConfig modelConfig,
                                                       HistoryWindowPolicy policy, int reservedTokens) {
        List<LlmMessage> full = buildFullLlmHistory(sessionId);
        List<LlmMessage> trimmed = ChatHistoryTrimmer.trim(full, policy, reservedTokens);
        return ChatHistoryTrimmer.droppedPrefix(full, trimmed);
    }

    /**
     * 将落库的 user 消息格式化为送入 LLM 的正文。
     * 有 mentions 或会话锚定 testFlowId 时重建完整块，否则回退 message_content 纯文本。
     */
    private static String formatUserMessageForLlm(AiChatMessageResult msg, Long testProjectId, Long testFlowId) {
        var mentions = AiDesignMentionSupport.parseMentionsFromMeta(msg.getResultMetaJson());
        if (!mentions.isEmpty() || testFlowId != null) {
            return AiDesignMentionSupport.buildUserLlmContent(
                    testProjectId,
                    testFlowId,
                    msg.getMessageContent(),
                    mentions);
        }
        return msg.getMessageContent() != null ? msg.getMessageContent() : "";
    }

    /** 从会话 biz_ref_json 解析 testFlowId */
    private static Long parseTestFlowId(String bizRefJson) {
        if (bizRefJson == null || bizRefJson.isBlank()) {
            return null;
        }
        try {
            JSONObject obj = JSON.parseObject(bizRefJson);
            Object id = obj.get("testFlowId");
            if (id == null) {
                return null;
            }
            if (id instanceof Number num) {
                return num.longValue();
            }
            String text = String.valueOf(id).trim();
            if (text.isEmpty()) {
                return null;
            }
            return Long.parseLong(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    private void insertMessage(Long sessionId, String role, String content, String thinkingContent,
                               String resultMeta, Long modelId) {
        AiChatMessage message = AiChatMessage.builder()
                .aiChatMessageId(IdUtil.getSnowflakeNextId())
                .aiChatSessionId(sessionId)
                .messageRole(role)
                .aiLlmModelId(modelId)
                .messageContent(content)
                .thinkingContent(thinkingContent)
                .resultMetaJson(resultMeta)
                .build();
        message.setCreateTime(DateUtils.getNowDate());
        aiChatMessageService.insertAiChatMessage(message);
    }

    private AiChatSession requireOwnedSession(Long sessionId, Long userId) {
        AiChatSession session = aiChatSessionService.selectAiChatSessionById(sessionId);
        if (session == null || (session.getDelStatus() != null && session.getDelStatus() != 0)) {
            throw new ServiceException("会话不存在");
        }
        if (session.getUserId() == null || !session.getUserId().equals(userId)) {
            throw new ServiceException("无权访问该会话");
        }
        return session;
    }

    /** 已有会话须与当前测试流 bizRef 一致，避免跨流复用 sessionId 污染上下文 */
    private static void validateSessionBizRef(AiChatSession session, String expectedBizRefJson) {
        if (session == null || expectedBizRefJson == null || expectedBizRefJson.isBlank()) {
            return;
        }
        Long expectedFlowId = parseTestFlowId(expectedBizRefJson);
        Long sessionFlowId = parseTestFlowId(session.getBizRefJson());
        if (expectedFlowId != null && sessionFlowId != null && !expectedFlowId.equals(sessionFlowId)) {
            throw new ServiceException("会话与当前测试流不匹配，请新建会话");
        }
    }

    private static String buildSessionTitle(String hint) {
        if (hint == null || hint.isBlank()) {
            return "新对话";
        }
        String trimmed = hint.trim();
        return trimmed.length() <= 20 ? trimmed : trimmed.substring(0, 20);
    }

    private static String buildTitleFromSummary(String summary) {
        String trimmed = summary.trim().replaceAll("\\s+", " ");
        if (trimmed.isEmpty()) {
            return "新对话";
        }
        return trimmed.length() <= 24 ? trimmed : trimmed.substring(0, 24);
    }

    /** 标题仍为占位或与首条 user 截断 prompt 一致时可刷新 */
    private static boolean shouldRefreshSessionTitle(String currentTitle, List<AiChatMessageResult> messages) {
        if (currentTitle == null || currentTitle.isBlank() || "新对话".equals(currentTitle.trim())) {
            return true;
        }
        String firstUser = messages.stream()
                .filter(m -> "user".equals(m.getMessageRole()))
                .map(AiChatMessageResult::getMessageContent)
                .filter(c -> c != null && !c.isBlank())
                .findFirst()
                .orElse(null);
        if (firstUser == null) {
            return false;
        }
        String truncatedUser = buildSessionTitle(firstUser);
        return currentTitle.trim().equals(truncatedUser);
    }

    /**
     * 从 assistant 消息提取送入 LLM 的摘要文本。
     * 优先读 resultMetaJson.summary；缺失时截取 messageContent 前 200 字作为兜底。
     */
    private static String extractAssistantSummary(AiChatMessageResult msg) {
        if (msg.getResultMetaJson() != null && !msg.getResultMetaJson().isBlank()) {
            try {
                JSONObject meta = JSON.parseObject(msg.getResultMetaJson());
                String summary = meta.getString("summary");
                if (summary != null && !summary.isBlank()) {
                    return summary;
                }
            } catch (Exception ignored) {
                // ignore
            }
        }
        String content = msg.getMessageContent();
        if (content == null) {
            return "";
        }
        return content.length() > 200 ? content.substring(0, 200) : content;
    }
}
