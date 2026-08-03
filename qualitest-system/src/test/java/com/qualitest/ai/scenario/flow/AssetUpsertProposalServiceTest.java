package com.qualitest.ai.scenario.flow;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.result.AiChatMessageResult;
import com.qualitest.ai.scenario.flow.model.AssetUpsertProposalDecisionRequest;
import com.qualitest.ai.scenario.flow.model.AssetUpsertProposalDecisionResult;
import com.qualitest.ai.service.AiChatConversationService;
import com.qualitest.ai.tools.AssetUpsertProposal;
import com.qualitest.project.params.TestProjectAssetSaveParams;
import com.qualitest.project.result.TestProjectAssetResult;
import com.qualitest.project.service.ITestProjectAssetService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.mockito.ArgumentCaptor;

import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 测 AssetUpsertProposalService：确认时写入素材库并改元数据；拒绝时只改元数据。
 * 边界：Mock 会话与素材服务，无 DB。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=AssetUpsertProposalServiceTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AssetUpsertProposalServiceTest {

    private static final Long USER_ID = 7L;
    private static final Long PROJECT_ID = 100L;
    private static final Long SESSION_ID = 200L;
    private static final Long MESSAGE_ID = 300L;

    private AiChatConversationService conversationService;
    private ITestProjectAssetService assetService;
    private AssetUpsertProposalService service;

    @BeforeEach
    void setUp() {
        conversationService = mock(AiChatConversationService.class);
        assetService = mock(ITestProjectAssetService.class);
        service = new AssetUpsertProposalService(conversationService, assetService);
    }

    /**
     * 前提：消息 meta 含 pending created 提案；库中无该 key。
     * 期望：insert 落盘；meta status=confirmed；ok=true。
     */
    @Test
    @Order(1)
    @DisplayName("确认新建提案时 insert 并更新 meta")
    void confirm_createsAssetAndMarksConfirmed() {
        when(conversationService.getMessageMeta(MESSAGE_ID, USER_ID)).thenReturn(messageWithProposal(
                AssetUpsertProposal.ACTION_CREATED, AssetUpsertProposal.STATUS_PENDING));
        when(conversationService.getSessionTestProjectId(SESSION_ID)).thenReturn(PROJECT_ID);
        when(assetService.selectTestProjectAssetResultByKey(PROJECT_ID, "clientAuth"))
                .thenThrow(new com.qualitest.common.exception.ServiceException("不存在"));
        when(assetService.insertTestProjectAsset(any())).thenAnswer(inv -> {
            TestProjectAssetSaveParams p = inv.getArgument(0);
            return TestProjectAssetResult.builder()
                    .id(1L)
                    .key(p.getKey())
                    .assets(p.getAssets())
                    .build();
        });

        AssetUpsertProposalDecisionResult result = service.confirm(request(), USER_ID);

        assertTrue(result.isOk());
        assertEquals(AssetUpsertProposal.STATUS_CONFIRMED, result.getStatus());
        verify(assetService).insertTestProjectAsset(any());
        ArgumentCaptor<String> metaCap = ArgumentCaptor.forClass(String.class);
        verify(conversationService).updateAssistantResultMeta(eq(MESSAGE_ID), eq(USER_ID), metaCap.capture());
        JSONObject meta = JSON.parseObject(metaCap.getValue());
        assertEquals("confirmed", meta.getJSONArray("assetProposals").getJSONObject(0).getString("status"));
    }

    /**
     * 前提：消息 meta 含 pending 提案。
     * 期望：不落盘；meta status=rejected。
     */
    @Test
    @Order(2)
    @DisplayName("拒绝提案时不落盘并标记 rejected")
    void reject_marksRejectedWithoutPersist() {
        when(conversationService.getMessageMeta(MESSAGE_ID, USER_ID)).thenReturn(messageWithProposal(
                AssetUpsertProposal.ACTION_UPDATED, AssetUpsertProposal.STATUS_PENDING));
        when(conversationService.getSessionTestProjectId(SESSION_ID)).thenReturn(PROJECT_ID);

        AssetUpsertProposalDecisionResult result = service.reject(request(), USER_ID);

        assertTrue(result.isOk());
        assertEquals(AssetUpsertProposal.STATUS_REJECTED, result.getStatus());
        verify(assetService, never()).insertTestProjectAsset(any());
        verify(assetService, never()).updateTestProjectAsset(any());
        ArgumentCaptor<String> metaCap = ArgumentCaptor.forClass(String.class);
        verify(conversationService).updateAssistantResultMeta(eq(MESSAGE_ID), eq(USER_ID), metaCap.capture());
        assertEquals("rejected",
                JSON.parseObject(metaCap.getValue()).getJSONArray("assetProposals").getJSONObject(0).getString("status"));
    }

    /**
     * 前提：提案已 confirmed。
     * 期望：ok=false，不重复落盘。
     */
    @Test
    @Order(3)
    @DisplayName("已确认提案再次确认失败")
    void confirm_alreadyConfirmed_fails() {
        when(conversationService.getMessageMeta(MESSAGE_ID, USER_ID)).thenReturn(messageWithProposal(
                AssetUpsertProposal.ACTION_CREATED, AssetUpsertProposal.STATUS_CONFIRMED));
        when(conversationService.getSessionTestProjectId(SESSION_ID)).thenReturn(PROJECT_ID);

        AssetUpsertProposalDecisionResult result = service.confirm(request(), USER_ID);

        assertFalse(result.isOk());
        verify(assetService, never()).insertTestProjectAsset(any());
        verify(conversationService, never()).updateAssistantResultMeta(any(), any(), any());
    }

    private static AssetUpsertProposalDecisionRequest request() {
        AssetUpsertProposalDecisionRequest req = new AssetUpsertProposalDecisionRequest();
        req.setTestProjectId(PROJECT_ID);
        req.setAiChatMessageId(MESSAGE_ID);
        req.setKey("clientAuth");
        return req;
    }

    private static AiChatMessageResult messageWithProposal(String action, String status) {
        JSONObject proposal = new JSONObject();
        proposal.put("key", "clientAuth");
        proposal.put("action", action);
        proposal.put("remark", "登录");
        proposal.put("status", status);
        proposal.put("fields", Map.of("mobile", "13800000001", "password", "Test@123456"));
        JSONArray arr = new JSONArray();
        arr.add(proposal);
        JSONObject meta = new JSONObject();
        meta.put("assetProposals", arr);
        return AiChatMessageResult.builder()
                .aiChatMessageId(MESSAGE_ID)
                .aiChatSessionId(SESSION_ID)
                .messageRole("assistant")
                .messageContent("ok")
                .resultMetaJson(meta.toJSONString())
                .createTime(new Date())
                .build();
    }
}
