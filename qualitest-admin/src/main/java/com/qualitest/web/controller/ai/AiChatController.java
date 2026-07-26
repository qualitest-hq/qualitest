package com.qualitest.web.controller.ai;

import com.qualitest.ai.params.CreateAiChatSessionRequest;
import com.qualitest.ai.params.UpdateAiChatSessionModelRequest;
import com.qualitest.ai.params.UpdateAiChatSessionThinkingRequest;
import com.qualitest.ai.result.AiChatMessageResult;
import com.qualitest.ai.result.AiChatSessionDetailResult;
import com.qualitest.ai.result.AiChatSessionResult;
import com.qualitest.ai.result.AiModelsListResult;
import com.qualitest.ai.service.AiChatConversationService;
import com.qualitest.ai.service.IAiLlmModelService;
import com.qualitest.common.core.controller.BaseController;
import com.qualitest.common.core.domain.R;
import com.qualitest.common.core.page.TableDataInfo;
import com.qualitest.project.service.ITestProjectMemberService;
import lombok.AllArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * AI 平台通用 HTTP 接口：模型列表、画布多轮会话。
 */
@RestController
@RequestMapping("/ai")
@AllArgsConstructor
public class AiChatController extends BaseController {

    private final IAiLlmModelService aiLlmModelService;
    private final AiChatConversationService aiChatConversationService;
    private final ITestProjectMemberService testProjectMemberService;

    /**
     * 返回按厂商分组的启用模型列表。
     */
    @GetMapping("/models")
    public R<AiModelsListResult> listModels() {
        return R.ok(aiLlmModelService.listModelsGrouped());
    }

    /**
     * 列出当前用户在指定场景与业务锚点下的会话。
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:query')")
    @GetMapping("/chat/session/list")
    public TableDataInfo listSessions(
            @RequestParam String scene,
            @RequestParam Long testProjectId,
            @RequestParam(required = false) Long testFlowId,
            @RequestParam(required = false) Long testProjectApiId) {
        testProjectMemberService.getCheckProjectMemberRole(testProjectId);
        List<AiChatSessionResult> rows = aiChatConversationService.listSessions(
                scene, testProjectId, testFlowId, testProjectApiId, getUserId());
        return getDataTable(rows);
    }

    /**
     * 获取会话详情及消息。
     *
     * @param messageDetail summary 时 assistant 消息不含 patchJson（含 hasPatch 标记）；full 返回完整 meta
     * @param limit         分页条数；不传或 ≤0 时返回全部消息（兼容旧调用）
     * @param beforeMessageId 游标：加载该 id 之前的更早消息；为空时加载最新 limit 条
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:query')")
    @GetMapping("/chat/session/{aiChatSessionId}")
    public R<AiChatSessionDetailResult> getSession(
            @PathVariable Long aiChatSessionId,
            @RequestParam(defaultValue = "summary") String messageDetail,
            @RequestParam(required = false) Integer limit,
            @RequestParam(required = false) Long beforeMessageId) {
        Long userId = getUserId();
        AiChatSessionDetailResult detail = "full".equalsIgnoreCase(messageDetail)
                ? aiChatConversationService.getSessionWithMessages(aiChatSessionId, userId, limit, beforeMessageId)
                : aiChatConversationService.getSessionWithMessageSummaries(
                        aiChatSessionId, userId, limit, beforeMessageId);
        testProjectMemberService.getCheckProjectMemberRole(detail.getSession().getTestProjectId());
        return R.ok(detail);
    }

    /**
     * 获取单条消息完整 meta（含 patchJson），用于 Lazy Patch 按需加载。
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:query')")
    @GetMapping("/chat/message/{aiChatMessageId}/meta")
    public R<AiChatMessageResult> getMessageMeta(@PathVariable Long aiChatMessageId) {
        Long userId = getUserId();
        AiChatMessageResult message = aiChatConversationService.getMessageMeta(aiChatMessageId, userId);
        testProjectMemberService.getCheckProjectMemberRole(
                aiChatConversationService.getSessionTestProjectId(message.getAiChatSessionId()));
        return R.ok(message);
    }

    /**
     * 新建空会话。
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:query')")
    @PostMapping("/chat/session")
    public R<AiChatSessionResult> createSession(@RequestBody CreateAiChatSessionRequest request) {
        testProjectMemberService.getCheckProjectMemberRole(request.getTestProjectId());
        return R.ok(aiChatConversationService.createSession(request, getUserId()));
    }

    /**
     * 更新会话当前模型。
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:query')")
    @PutMapping("/chat/session/{aiChatSessionId}/model")
    public R<Void> updateSessionModel(
            @PathVariable Long aiChatSessionId,
            @RequestBody UpdateAiChatSessionModelRequest body) {
        AiChatSessionDetailResult detail = aiChatConversationService.getSessionWithMessages(
                aiChatSessionId, getUserId());
        testProjectMemberService.getCheckProjectMemberRole(detail.getSession().getTestProjectId());
        aiChatConversationService.updateSessionModel(aiChatSessionId, body.getCurrentModelId(), getUserId());
        return R.ok();
    }

    /**
     * 更新会话思考开关。
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:query')")
    @PutMapping("/chat/session/{aiChatSessionId}/thinking")
    public R<Void> updateSessionThinking(
            @PathVariable Long aiChatSessionId,
            @RequestBody UpdateAiChatSessionThinkingRequest body) {
        AiChatSessionDetailResult detail = aiChatConversationService.getSessionWithMessages(
                aiChatSessionId, getUserId());
        testProjectMemberService.getCheckProjectMemberRole(detail.getSession().getTestProjectId());
        if (body.getThinkingEnabled() == null) {
            return R.fail("thinkingEnabled 不能为空");
        }
        aiChatConversationService.updateSessionThinking(aiChatSessionId, body.getThinkingEnabled(), getUserId());
        return R.ok();
    }

    /**
     * 从锚点消息起截断会话后续消息（用于重新生成 / 编辑重发）。
     *
     * @param inclusive true 时连同锚点消息一并删除
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:query')")
    @DeleteMapping("/chat/session/{aiChatSessionId}/messages/truncate-after/{anchorMessageId}")
    public R<Integer> truncateMessagesAfter(
            @PathVariable Long aiChatSessionId,
            @PathVariable Long anchorMessageId,
            @RequestParam(defaultValue = "false") boolean inclusive) {
        AiChatSessionDetailResult detail = aiChatConversationService.getSessionWithMessages(
                aiChatSessionId, getUserId());
        testProjectMemberService.getCheckProjectMemberRole(detail.getSession().getTestProjectId());
        int deleted = aiChatConversationService.truncateMessagesAfter(
                aiChatSessionId, anchorMessageId, inclusive, getUserId());
        return R.ok(deleted);
    }

    /**
     * 删除会话及其消息。
     */
    @PreAuthorize("@ss.hasPermi('project:testProject:query')")
    @DeleteMapping("/chat/session/{aiChatSessionId}")
    public R<Void> deleteSession(@PathVariable Long aiChatSessionId) {
        AiChatSessionDetailResult detail = aiChatConversationService.getSessionWithMessages(
                aiChatSessionId, getUserId());
        testProjectMemberService.getCheckProjectMemberRole(detail.getSession().getTestProjectId());
        aiChatConversationService.deleteSession(aiChatSessionId, getUserId());
        return R.ok();
    }
}
