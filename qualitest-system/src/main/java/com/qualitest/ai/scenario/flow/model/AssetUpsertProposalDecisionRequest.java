package com.qualitest.ai.scenario.flow.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

/**
 * 用户确认或拒绝某条素材库写入提案时的请求体。
 */
@Data
public class AssetUpsertProposalDecisionRequest {

    /** 测试项目 id，用于成员权限校验，并与消息所属项目核对 */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testProjectId;

    /** 存有 assetProposals 的助手消息 id */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long aiChatMessageId;

    /** 要处理的素材 key */
    private String key;
}
