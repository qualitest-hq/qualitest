package com.qualitest.ai.scenario.flow.model;

import com.alibaba.fastjson2.JSONObject;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.qualitest.ai.tools.AssetUpsertProposal;
import com.qualitest.ai.tools.AuthProfileUpsertProposal;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 测试流 AI 设计接口的一轮响应。
 * <p>
 * 含自然语言说明、画布增量 patch、校验结果、素材提案、多端 Profile 提案（可含响应约定变更），
 * 以及本轮工具轨迹。
 * explainOnly=true：本轮没有可展示的 Staging patch。
 * interrupted=true：本轮因用户取消或连接中断结束，摘要与 patch 可能不完整。
 * 半自动下 patch / 素材 / 多端 Profile 提案须用户确认后再持久化；全自动下图与配置可能已在本轮写库。
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

    /** true：本轮未成功调用任何 submit_*，仅说明或仅有素材/多端 Profile 提案 */
    private final boolean explainOnly;

    /**
     * 本轮素材库写入提案列表（含 fields 明文，用于确认卡片展示与落盘）。
     * 无提案时为 null 或空列表。会话列表接口会对 fields 脱敏。
     */
    private final List<AssetUpsertProposal> assetProposals;

    /**
     * 本轮多端 Profile 写入提案（含 before/after/patch；patch 可含响应约定四字段）。
     * 无提案时为 null 或空列表。
     */
    private final List<AuthProfileUpsertProposal> authProfileProposals;

    /**
     * 本轮工具调用轨迹（已脱敏截断）。
     * 含步数、是否截断及按序的 name / ok / ms / args / result。
     */
    private final JSONObject toolTrace;

    /** 本轮因用户取消或连接中断而结束，助手内容可能不完整 */
    private final boolean interrupted;
}
