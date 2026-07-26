package com.qualitest.ai.params;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

/**
 * 创建 AI 会话请求体（画布专用 API）。
 */
@Getter
@Setter
public class CreateAiChatSessionRequest {

    private String sessionScene;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testProjectId;

    /** 业务锚点 JSON，如 {"testFlowId":"..."} */
    private String bizRefJson;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long currentModelId;

    private String sessionTitle;

    /** 创建会话时的思考开关，true 开 false 关，省略则 NULL（跟随模型默认） */
    private Boolean thinkingEnabled;
}
