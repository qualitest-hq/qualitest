package com.qualitest.ai.scenario.flow.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;

/**
 * 测试流 AI 设计接口响应体。
 * <p>
 * 仅返回建议 patch 与校验结果，不自动写入 test_flow 表；
 * 用户在前端确认合并后仍需手动保存画布。
 */
@Getter
@Builder
public class TestFlowDesignResult {

    /** 会话 id；多轮会话落库功能启用后填充 */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private final Long aiChatSessionId;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private final Long aiLlmModelId;

    /** 本次设计使用的厂商展示名 */
    private final String vendorName;
    /** 本次设计使用的模型展示名 */
    private final String modelName;

    /** 中文流程说明 */
    private final String summary;

    /** 模型思考过程（Extended Thinking 等），仅展示用 */
    private final String thinkingContent;

    /** 规范化后的增量 patch，供前端 Diff */
    private final FlowDesignPatch patch;

    /** 将 patch 预合并到请求图后的校验结果 */
    private final DesignValidationResult validation;

    /** true 表示仅解释、不产生 patch（explain 模式） */
    private final boolean explainOnly;
}
