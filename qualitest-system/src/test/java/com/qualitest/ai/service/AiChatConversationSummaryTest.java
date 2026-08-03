package com.qualitest.ai.service;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.qualitest.ai.result.AiChatMessageResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 测会话消息列表摘要：去掉画布 patch 全文与素材提案字段明文。
 * 边界：纯静态 toMessageSummary，无 DB。
 * 单跑：mvn test -DskipTests=false -pl qualitest-system -am -Dtest=AiChatConversationSummaryTest
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AiChatConversationSummaryTest {

    /**
     * 前提：assistant meta 含 patchJson 与带明文 fields 的 assetProposals。
     * 期望：摘要去掉 patchJson 与 fields，标 hasPatch / hasAssetProposals，保留 fieldNames。
     */
    @Test
    @Order(1)
    @DisplayName("摘要脱敏 patch 与素材提案 fields")
    void toMessageSummary_redactsPatchAndAssetFields() {
        JSONObject proposal = new JSONObject();
        proposal.put("key", "clientAuth");
        proposal.put("action", "created");
        proposal.put("status", "pending");
        proposal.put("fields", Map.of("password", "SECRET"));
        JSONArray arr = new JSONArray();
        arr.add(proposal);
        JSONObject meta = new JSONObject();
        meta.put("explainOnly", false);
        meta.put("patchJson", Map.of("summary", "改图"));
        meta.put("assetProposals", arr);

        AiChatMessageResult message = AiChatMessageResult.builder()
                .aiChatMessageId(1L)
                .aiChatSessionId(2L)
                .messageRole("assistant")
                .messageContent("ok")
                .resultMetaJson(meta.toJSONString())
                .createTime(new Date())
                .build();

        AiChatMessageResult summary = AiChatConversationService.toMessageSummary(message);
        JSONObject out = JSON.parseObject(summary.getResultMetaJson());
        assertTrue(out.getBooleanValue("hasPatch"));
        assertFalse(out.containsKey("patchJson"));
        assertTrue(out.getBooleanValue("hasAssetProposals"));
        JSONObject safeProposal = out.getJSONArray("assetProposals").getJSONObject(0);
        assertFalse(safeProposal.containsKey("fields"));
        assertTrue(safeProposal.getJSONArray("fieldNames").contains("password"));
        assertEquals("pending", safeProposal.getString("status"));
        assertFalse(summary.getResultMetaJson().contains("SECRET"));
    }
}
