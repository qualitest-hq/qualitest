package com.qualitest.ai.scenario.flow.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.qualitest.ai.tools.AssetUpsertProposal;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 测试流 AI 设计接口的一轮响应。
 * <p>
 * 含自然语言说明、画布增量 patch、校验结果，以及本轮素材库写入提案（若有）。
 * explainOnly=true 表示本轮未成功接受任何 submit_* 单元，无画布 patch。
 * 不自动保存测试流，也不自动写入素材库；均需用户确认后再落盘。
 */
@Getter
@Builder
public class TestFlowDesignResult {

    /** 本轮会话 id */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private final Long aiChatSessionId;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private final Long aiLlmModelId;

    /** 本次使用的厂商展示名 */
    private final String vendorName;
    /** 本次使用的模型展示名 */
    private final String modelName;

    /** 给用户看的中文说明（助手气泡正文） */
    private final String summary;

    /** 模型思考过程全文，仅展示，不参与多轮上下文 */
    private final String thinkingContent;

    /** 本轮累积的画布增量；explainOnly 或无成功单元时为 null */
    private final FlowDesignPatch patch;

    /** 最近一次成功 submit 的校验摘要；纯答疑时 ok=true 且无错误 */
    private final DesignValidationResult validation;

    /** true：本轮未成功调用任何 submit_*，仅说明或仅有素材提案 */
    private final boolean explainOnly;

    /**
     * 本轮素材库写入提案列表（含 fields 明文，供前端确认卡片展示与落盘）。
     * 无提案时为 null 或空列表。会话列表接口会对 fields 脱敏。
     */
    private final List<AssetUpsertProposal> assetProposals;
}
