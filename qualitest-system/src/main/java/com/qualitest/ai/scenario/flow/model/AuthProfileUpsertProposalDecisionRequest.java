package com.qualitest.ai.scenario.flow.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Data;

/**
 * 确认或拒绝项目鉴权 Profile 写入提案的请求体。
 */
@Data
public class AuthProfileUpsertProposalDecisionRequest {

    /** 测试项目 id；确认落盘前会校验是否属于该会话 */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testProjectId;

    /** 含 authProfileProposals 的助手消息 id */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long aiChatMessageId;

    /** 提案中的 Profile id */
    private String profileId;
}
