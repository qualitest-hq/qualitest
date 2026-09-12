package com.qualitest.ai.tools.apidesign;

import com.qualitest.ai.config.AiLlmConfigService;
import lombok.Builder;
import lombok.Getter;

/**
 * API 设计工具执行时的请求级上下文。
 * <p>
 * 含当前项目/接口、编辑器脚本草稿、工作台摘要、submit 捕获器，以及本轮是否全自动。
 */
@Getter
@Builder
public class ApiDesignToolContext {

    /** 测试项目 id */
    private final Long testProjectId;

    /** 当前接口 id */
    private final Long testProjectApiId;

    /** 前端传入的前置脚本草稿 */
    private final String preRequestScript;

    /** 前端传入的后置脚本草稿 */
    private final String postRequestScript;

    /** 可选工作台草稿摘要；有则在 context 工具中回显给模型 */
    private final String workbenchSnapshot;

    /**
     * 本轮 submit_api_design_patch 的捕获器。
     * Agent 据此判断是否产出了结构化 patch。
     */
    private final ApiDesignSubmitCapture submitCapture;

    /**
     * 是否全自动。
     * 影响 submit 回执文案与 system 是否追加全自动规程；真正合并进工作台由前端完成。
     */
    @Builder.Default
    private final boolean autopilotEnabled = false;

    /** 工具返回 JSON 的最大字节数，超限会被截断 */
    @Builder.Default
    private final int maxToolResultBytes = AiLlmConfigService.DEFAULT_MAX_TOOL_RESULT_BYTES;
}
