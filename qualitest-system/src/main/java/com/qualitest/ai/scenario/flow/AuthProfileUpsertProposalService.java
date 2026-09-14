package com.qualitest.ai.scenario.flow;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.result.AiChatMessageResult;
import com.qualitest.ai.scenario.flow.model.AuthProfileUpsertProposalDecisionRequest;
import com.qualitest.ai.scenario.flow.model.AuthProfileUpsertProposalDecisionResult;
import com.qualitest.ai.service.AiChatConversationService;
import com.qualitest.ai.tools.AuthProfileUpsertProposal;
import com.qualitest.ai.tools.support.AuthProfileUpsertSupport;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.project.mapper.TestProjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 处理 AI 设计助手消息中项目鉴权 Profile 写入提案的确认与拒绝。
 * <p>
 * 确认：按提案 patch 浅合并写入项目 auth_config，并更新消息 meta 中该条 status；
 * 拒绝：只改 meta 状态为 rejected，不改项目鉴权。
 */
@Service
@RequiredArgsConstructor
public class AuthProfileUpsertProposalService {

    private final AiChatConversationService aiChatConversationService;
    private final TestProjectMapper testProjectMapper;

    /**
     * 确认提案：落盘 patch，并将消息 meta 中对应提案标为 confirmed。
     *
     * @param request 含项目 id、助手消息 id、profileId
     * @param userId  当前用户 id
     * @return 决策结果
     */
    @Transactional(rollbackFor = Exception.class)
    public AuthProfileUpsertProposalDecisionResult confirm(
            AuthProfileUpsertProposalDecisionRequest request, Long userId) {
        return decide(request, userId, true);
    }

    /**
     * 拒绝提案：不写库，仅将消息 meta 中对应提案标为 rejected。
     *
     * @param request 含项目 id、助手消息 id、profileId
     * @param userId  当前用户 id
     * @return 决策结果
     */
    @Transactional(rollbackFor = Exception.class)
    public AuthProfileUpsertProposalDecisionResult reject(
            AuthProfileUpsertProposalDecisionRequest request, Long userId) {
        return decide(request, userId, false);
    }

    /**
     * 确认/拒绝共用逻辑：校验请求与会话归属，定位 meta 中提案后落盘或改状态。
     *
     * @param confirm true 确认落盘，false 拒绝
     */
    private AuthProfileUpsertProposalDecisionResult decide(
            AuthProfileUpsertProposalDecisionRequest request, Long userId, boolean confirm) {
        List<String> errors = validateRequest(request);
        if (!errors.isEmpty()) {
            return fail(errors);
        }
        if (userId == null) {
            return fail(List.of("未登录"));
        }

        AiChatMessageResult message = aiChatConversationService.getMessageMeta(request.getAiChatMessageId(), userId);
        Long sessionProjectId = aiChatConversationService.getSessionTestProjectId(message.getAiChatSessionId());
        if (sessionProjectId == null || !sessionProjectId.equals(request.getTestProjectId())) {
            return fail(List.of("消息与测试项目不匹配"));
        }

        JSONObject meta = parseMeta(message.getResultMetaJson());
        JSONArray proposals = meta.getJSONArray("authProfileProposals");
        if (proposals == null || proposals.isEmpty()) {
            return fail(List.of("该消息无鉴权 Profile 提案"));
        }

        int index = findProposalIndex(proposals, request.getProfileId());
        if (index < 0) {
            return fail(List.of("未找到 profileId=" + request.getProfileId() + " 的提案"));
        }
        JSONObject proposalJson = proposals.getJSONObject(index);
        String status = proposalJson.getString("status");
        if (AuthProfileUpsertProposal.STATUS_CONFIRMED.equals(status)) {
            return fail(List.of("该提案已确认"));
        }
        if (AuthProfileUpsertProposal.STATUS_REJECTED.equals(status)) {
            return fail(List.of("该提案已拒绝"));
        }

        String action = proposalJson.getString("action");
        if (action == null || action.isBlank()) {
            action = AuthProfileUpsertProposal.ACTION_UPDATED;
        }

        Map<String, Object> after = null;
        if (confirm) {
            Map<String, Object> patch = AuthProfileUpsertSupport.parsePatch(proposalJson.get("patch"));
            if (patch == null) {
                return fail(List.of("提案缺少 patch，无法落盘"));
            }
            boolean create = AuthProfileUpsertProposal.ACTION_CREATED.equals(action);
            try {
                String id = AuthProfileUpsertSupport.persistPatch(
                        testProjectMapper,
                        request.getTestProjectId(),
                        request.getProfileId(),
                        create,
                        patch);
                proposalJson.put("profileId", id);
                proposalJson.put("status", AuthProfileUpsertProposal.STATUS_CONFIRMED);
                after = proposalJson.getObject("after", Map.class);
                if (after != null) {
                    after.put("id", id);
                    proposalJson.put("after", after);
                }
            } catch (ServiceException ex) {
                return fail(List.of(ex.getMessage() != null ? ex.getMessage() : "写入项目鉴权失败"));
            }
        } else {
            proposalJson.put("status", AuthProfileUpsertProposal.STATUS_REJECTED);
        }
        proposals.set(index, proposalJson);
        meta.put("authProfileProposals", proposals);
        aiChatConversationService.updateAssistantResultMeta(
                request.getAiChatMessageId(), userId, meta.toJSONString());

        @SuppressWarnings("unchecked")
        List<String> changed = proposalJson.getObject("changedFields", List.class);
        return AuthProfileUpsertProposalDecisionResult.builder()
                .ok(true)
                .errors(List.of())
                .profileId(proposalJson.getString("profileId"))
                .action(action)
                .status(confirm
                        ? AuthProfileUpsertProposal.STATUS_CONFIRMED
                        : AuthProfileUpsertProposal.STATUS_REJECTED)
                .changedFields(changed)
                .after(after != null ? after : proposalJson.getObject("after", Map.class))
                .build();
    }

    /** 校验决策请求必填字段，返回错误文案列表（空表示通过） */
    private static List<String> validateRequest(AuthProfileUpsertProposalDecisionRequest request) {
        List<String> errors = new ArrayList<>();
        if (request == null) {
            errors.add("缺少请求体");
            return errors;
        }
        if (request.getTestProjectId() == null) {
            errors.add("缺少 testProjectId");
        }
        if (request.getAiChatMessageId() == null) {
            errors.add("缺少 aiChatMessageId");
        }
        if (request.getProfileId() == null || request.getProfileId().isBlank()) {
            errors.add("缺少 profileId");
        }
        return errors;
    }

    /** 组装失败结果（ok=false） */
    private static AuthProfileUpsertProposalDecisionResult fail(List<String> errors) {
        return AuthProfileUpsertProposalDecisionResult.builder()
                .ok(false)
                .errors(errors)
                .build();
    }

    /** 解析助手消息 result_meta_json；空串返回空对象，非法 JSON 抛业务异常 */
    private static JSONObject parseMeta(String metaJson) {
        if (metaJson == null || metaJson.isBlank()) {
            return new JSONObject();
        }
        try {
            JSONObject meta = JSON.parseObject(metaJson);
            return meta != null ? meta : new JSONObject();
        } catch (Exception ex) {
            throw new ServiceException("消息元数据损坏");
        }
    }

    /**
     * 在提案数组中按 profileId 定位下标。
     *
     * @return 下标；未找到为 -1
     */
    private static int findProposalIndex(JSONArray proposals, String profileId) {
        String want = profileId.trim();
        for (int i = 0; i < proposals.size(); i++) {
            JSONObject item = proposals.getJSONObject(i);
            if (item != null && want.equals(item.getString("profileId"))) {
                return i;
            }
        }
        return -1;
    }
}
