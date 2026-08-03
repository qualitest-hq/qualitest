package com.qualitest.ai.scenario.flow;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.result.AiChatMessageResult;
import com.qualitest.ai.scenario.flow.model.AssetUpsertProposalDecisionRequest;
import com.qualitest.ai.scenario.flow.model.AssetUpsertProposalDecisionResult;
import com.qualitest.ai.service.AiChatConversationService;
import com.qualitest.ai.tools.AssetUpsertProposal;
import com.qualitest.ai.tools.support.AssetUpsertSupport;
import com.qualitest.common.exception.ServiceException;
import com.qualitest.project.params.TestProjectAssetSaveParams;
import com.qualitest.project.result.TestProjectAssetResult;
import com.qualitest.project.service.ITestProjectAssetService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 素材库 AI 写入提案的确认与拒绝。
 * <p>
 * 从指定助手消息的元数据中读取提案：确认则写入项目素材库并标记 confirmed；
 * 拒绝则只标记 rejected，不写素材库。处理结果写回该消息的元数据。
 */
@Service
@RequiredArgsConstructor
public class AssetUpsertProposalService {

    private final AiChatConversationService aiChatConversationService;
    private final ITestProjectAssetService testProjectAssetService;

    /**
     * 确认提案：把 fields 写入素材库（新增或更新），元数据状态改为 confirmed。
     */
    @Transactional(rollbackFor = Exception.class)
    public AssetUpsertProposalDecisionResult confirm(AssetUpsertProposalDecisionRequest request, Long userId) {
        return decide(request, userId, true);
    }

    /**
     * 拒绝提案：不写素材库，元数据状态改为 rejected。
     */
    @Transactional(rollbackFor = Exception.class)
    public AssetUpsertProposalDecisionResult reject(AssetUpsertProposalDecisionRequest request, Long userId) {
        return decide(request, userId, false);
    }

    /**
     * 确认或拒绝的共用流程：校验 → 读消息提案 → 落盘或仅改状态 → 更新消息元数据。
     *
     * @param confirm true 确认落盘，false 拒绝
     */
    private AssetUpsertProposalDecisionResult decide(AssetUpsertProposalDecisionRequest request,
                                                     Long userId,
                                                     boolean confirm) {
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
        JSONArray proposals = meta.getJSONArray("assetProposals");
        if (proposals == null || proposals.isEmpty()) {
            return fail(List.of("该消息无素材提案"));
        }

        int index = findProposalIndex(proposals, request.getKey());
        if (index < 0) {
            return fail(List.of("未找到 key=" + request.getKey() + " 的素材提案"));
        }
        JSONObject proposalJson = proposals.getJSONObject(index);
        String status = proposalJson.getString("status");
        if (AssetUpsertProposal.STATUS_CONFIRMED.equals(status)) {
            return fail(List.of("该提案已确认"));
        }
        if (AssetUpsertProposal.STATUS_REJECTED.equals(status)) {
            return fail(List.of("该提案已拒绝"));
        }

        String action = proposalJson.getString("action");
        if (action == null || action.isBlank()) {
            action = AssetUpsertProposal.ACTION_CREATED;
        }

        if (confirm) {
            Map<String, Object> fields = AssetUpsertSupport.parseFlatFields(proposalJson.get("fields"));
            if (fields == null) {
                return fail(List.of("提案缺少 fields，无法落盘"));
            }
            try {
                persistAsset(request.getTestProjectId(), request.getKey(), fields, proposalJson.getString("remark"));
            } catch (ServiceException ex) {
                return fail(List.of(ex.getMessage() != null ? ex.getMessage() : "写入素材库失败"));
            }
            proposalJson.put("status", AssetUpsertProposal.STATUS_CONFIRMED);
        } else {
            proposalJson.put("status", AssetUpsertProposal.STATUS_REJECTED);
        }
        proposals.set(index, proposalJson);
        meta.put("assetProposals", proposals);
        aiChatConversationService.updateAssistantResultMeta(
                request.getAiChatMessageId(), userId, meta.toJSONString());

        return AssetUpsertProposalDecisionResult.builder()
                .ok(true)
                .errors(List.of())
                .key(request.getKey())
                .action(action)
                .status(confirm ? AssetUpsertProposal.STATUS_CONFIRMED : AssetUpsertProposal.STATUS_REJECTED)
                .fields(AssetUpsertSupport.extractFieldNames(proposalJson))
                .placeholderHint("{{asset." + request.getKey() + ".<field>}}")
                .build();
    }

    /**
     * 把提案字段写入项目素材库。
     * 当前库中无该 key 则新增，已有则按 id 更新；备注为空时更新保留原备注。
     */
    private void persistAsset(Long projectId, String key, Map<String, Object> fields, String remark) {
        Map<String, Object> assets = new LinkedHashMap<>();
        assets.put(key, fields);
        TestProjectAssetSaveParams.TestProjectAssetSaveParamsBuilder params = TestProjectAssetSaveParams.builder()
                .testProjectId(projectId)
                .key(key)
                .assets(assets);

        TestProjectAssetResult existing = AssetUpsertSupport.findByKeyOrNull(
                testProjectAssetService, projectId, key);
        if (existing == null) {
            testProjectAssetService.insertTestProjectAsset(params.remark(remark).build());
        } else {
            testProjectAssetService.updateTestProjectAsset(params
                    .id(existing.getId())
                    .remark(remark != null ? remark : existing.getRemark())
                    .build());
        }
    }

    /** 校验请求必填：testProjectId、aiChatMessageId、key */
    private static List<String> validateRequest(AssetUpsertProposalDecisionRequest request) {
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
        if (request.getKey() == null || request.getKey().isBlank()) {
            errors.add("缺少 key");
        }
        return errors;
    }

    /** 组装失败响应 */
    private static AssetUpsertProposalDecisionResult fail(List<String> errors) {
        return AssetUpsertProposalDecisionResult.builder()
                .ok(false)
                .errors(errors)
                .build();
    }

    /** 解析助手消息的 resultMetaJson；空串得到空对象，非法 JSON 抛业务异常 */
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

    /** 在提案数组中按 key 查找下标；找不到返回 -1 */
    private static int findProposalIndex(JSONArray proposals, String key) {
        String want = key.trim();
        for (int i = 0; i < proposals.size(); i++) {
            JSONObject item = proposals.getJSONObject(i);
            if (item != null && want.equals(item.getString("key"))) {
                return i;
            }
        }
        return -1;
    }
}
