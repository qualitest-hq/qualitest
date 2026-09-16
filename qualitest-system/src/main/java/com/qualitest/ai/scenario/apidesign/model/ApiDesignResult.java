package com.qualitest.ai.scenario.apidesign.model;

import com.alibaba.fastjson2.JSONObject;
import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.Builder;
import lombok.Getter;

/**
 * AI API 助手单轮对话完成后的响应。
 * 含说明文案、结构化 patch、校验结果、工具轨迹 toolTrace；
 * interrupted=true 表示本轮因取消或断连结束，内容可能不完整。
 */
@Getter
@Builder
public class ApiDesignResult {

    /** 本轮使用的聊天会话 id（字符串，避免前端精度丢失） */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private final Long aiChatSessionId;

    /** 本轮实际调用的模型 id */
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private final Long aiLlmModelId;

    /** 模型厂商名称，展示用 */
    private final String vendorName;

    /** 模型名称，展示用 */
    private final String modelName;

    /** 助手气泡正文（优先用 patch.summary） */
    private final String summary;

    /** 模型思考链内容（若开启思考且有输出） */
    private final String thinkingContent;

    /** 结构化修改建议；仅答疑时为 null */
    private final ApiDesignPatch patch;

    /** patch 校验结果；仅答疑时视为通过 */
    private final ApiDesignValidationResult validation;

    /**
     * true：本轮未提交结构化 patch，仅返回说明文字；
     * false：已提交 patch，侧栏展示变更勾选区。
     */
    private final boolean explainOnly;

    /**
     * 本轮工具调用轨迹（已脱敏截断）。
     * 含步数、是否截断及按序的 name / ok / ms / args / result。
     */
    private final JSONObject toolTrace;

    /** 本轮因用户取消或连接中断而结束，助手内容可能不完整 */
    private final boolean interrupted;
}
