package com.qualitest.ai.scenario.apidesign.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Getter;
import lombok.Setter;

/**
 * AI API 助手流式设计请求体。
 */
@Getter
@Setter
public class ApiDesignRequest {

    /** 测试项目 id */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testProjectId;

    /** 当前正在设计的项目 API id */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long testProjectApiId;

    /** 用户选定的大模型 id */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long aiLlmModelId;

    /** 已有会话 id；空则服务端新建会话 */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Long aiChatSessionId;

    /** 用户自然语言需求 */
    private String prompt;

    /** 调试/设计页当前前置脚本草稿（可空字符串） */
    private String preRequestScript;

    /** 调试/设计页当前后置脚本草稿（可空字符串） */
    private String postRequestScript;

    /**
     * 可选工作台草稿摘要 JSON，携带未保存的约束或测值等上下文。
     */
    private String workbenchSnapshot;

    /** 本轮是否开启思考链；新建会话时写入会话配置 */
    private Boolean thinkingEnabled;

    /**
     * 是否开启全自动：true 时追加全自动规程；前端会自动应用 Diff 到工作台草稿。
     * 默认 false（半自动：Diff 人审）。不自动保存接口库。
     */
    private Boolean autopilotEnabled;

    /** 本请求是否启用全自动 */
    public boolean isAutopilotEnabledEffective() {
        return Boolean.TRUE.equals(autopilotEnabled);
    }
}
